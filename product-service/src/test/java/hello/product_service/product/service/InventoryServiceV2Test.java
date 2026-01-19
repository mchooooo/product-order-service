package hello.product_service.product.service;

import hello.product_service.product.domain.Product;
import hello.product_service.product.domain.ProductStatus;
import hello.product_service.product.domain.StockStrategy;
import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.infra.TestContainerInitializer;
import hello.product_service.product.infra.redis.StockRedisManager;
import hello.product_service.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers // Testcontainers 사용을 선언
@ContextConfiguration(initializers = TestContainerInitializer.class) // 초기화 클래스 지정
class InventoryServiceV2Test {

    private static final int INITIAL_STOCK = 100;
    private Long PRODUCT_ID_HOT = 100L;

    @Autowired
    private InventoryServiceV2 inventoryService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    StockRedisManager stockRedisManager;


    @BeforeEach
    void setup() {
        // DB에 초기 재고 설정 (HOT 상품)
        Product hotProduct = new Product("TEST", 1000, INITIAL_STOCK, ProductStatus.ACTIVE, StockStrategy.REDIS_FIRST);
        Product saveProduct = productRepository.saveAndFlush(hotProduct);
        PRODUCT_ID_HOT = saveProduct.getId();

        System.out.println("======== "+ PRODUCT_ID_HOT +" =========");
        // Redis에 초기 재고 설정 (DB와 동일하게)
        stockRedisManager.initializeStock(PRODUCT_ID_HOT, INITIAL_STOCK);
    }

    @Test
    void hotItem_레디스_성공_디비_불일치시_레디스_복구_테스트() {
        // given
        // Redis 재고를 DB 재고보다 높게 설정 (불일치 유도)
        stockRedisManager.initializeStock(PRODUCT_ID_HOT, INITIAL_STOCK + 50);

        // 120개 감소 요청
        int requestQty = 120;

        // when
        assertThatThrownBy(() -> {
            inventoryService.decreaseByOrder(PRODUCT_ID_HOT, 1L, requestQty, "DEC-1");
        }).isInstanceOf(InsufficientStockException.class);

        // then
        // DB 재고 롤백 -> 초기 재고와 같아야 한다.
        Product findProduct = productRepository.findById(PRODUCT_ID_HOT).get();
        assertThat(findProduct.getStock()).isEqualTo(INITIAL_STOCK);

        // Redis 재고 복구 -> 초기 설정 값 150으로 복구 확인
        int redisStock = stockRedisManager.findStock(PRODUCT_ID_HOT).intValue();
        assertThat(redisStock).isEqualTo(INITIAL_STOCK + 50);
    }

    @Test
    void hotItem_레디스_선점_실패시_디비_접근_X_테스트() {
        // given
        // 레디스 초기화, DB보다 적게
        stockRedisManager.initializeStock(PRODUCT_ID_HOT, 5);
        // 레디스 초기화 된 값보다 많이 요청
        int requestQty = 10;

        // when
        assertThatThrownBy(() -> {
            inventoryService.decreaseByOrder(PRODUCT_ID_HOT, 2L, requestQty, "DEC-2");
        }).isInstanceOf(InsufficientStockException.class);

        // then
        // DB 재고 처음에 설정한 100 확인
        Product findProduct = productRepository.findById(PRODUCT_ID_HOT).get();
        assertThat(findProduct.getStock()).isEqualTo(INITIAL_STOCK);
    }

    @Test
    void hotItem_동시성_검증_재고는_0이_되어야한다() throws InterruptedException {
        int threadCount = 100; // 동시 요청 수
        int executionCount = INITIAL_STOCK; // 총 성공해야 하는 요청 수 (100개)

        // 스레드 풀 설정
        /*
            ExecutorService
            스레드를 미리 만들어두고, 던져주는 작업(Task)을 스레드들에게 배분하여 실행
            32개의 스레드를 가진 풀 생성
         */
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        /*
            CountDownLatch
            지정한 횟수만큼 countDown()이 호출될 때까지 메인 스레드를 멈춤(await()).
            new CountDownLatch(100): 100번의 신호를 기다림
         */
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong successCount = new AtomicLong(0); // 멀티 스레드에서 안전한 값

        // DB/Redis 모두 100개로 시작 (setup에서 이미 수행)

        for (int i = 0; i < threadCount; i++) {
            final int requestId = i;
            executorService.submit(() -> {
                try {
                    // 모든 요청은 1개씩 감소를 시도
                    inventoryService.decreaseByOrder(PRODUCT_ID_HOT, (long) requestId, 1, "concurrency-" + requestId);
                    successCount.incrementAndGet(); // 성공한 경우만 카운트
                } catch (InsufficientStockException e) {
                    // 재고 부족으로 인한 실패는 정상으로 간주
                } catch (Exception e) {
                    // 시스템 오류 발생 시 테스트 실패
                    // (로깅 후 fail() 호출)
                } finally {
                    latch.countDown(); // 작업을 마칠 때마다 숫자 하나 감소
                }
            });
        }

        latch.await(); // 모든 스레드가 종료될 때까지 대기, 숫자가 0이 될 때까지 대기
        executorService.shutdown();

        // 최종 DB 재고가 정확히 0인지 확인 (DB 정합성)
        Product finalDbProduct = productRepository.findById(PRODUCT_ID_HOT).get();
        assertThat(finalDbProduct.getStock()).isZero();

        // 최종 Redis 재고가 정확히 0인지 확인 (Redis 정합성)
        Long finalRedisStock = stockRedisManager.findStock(PRODUCT_ID_HOT);
        assertThat(finalRedisStock).isZero();

        // 최종 성공 요청 수가 초기 재고 수와 일치하는지 확인 (Race Condition 방지)
        assertThat(successCount.get()).isEqualTo(executionCount);

    }


}