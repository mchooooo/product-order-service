package hello.product_service.product.service;

import hello.product_service.product.domain.Direction;
import hello.product_service.product.domain.IdempotencyRecord;
import hello.product_service.product.domain.IdempotencyStatus;
import hello.product_service.product.domain.Product;
import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.model.StockResult;
import hello.product_service.product.repository.IdempotencyRepository;
import hello.product_service.product.repository.ProductRepository;
import org.hibernate.exception.LockTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class InventoryServiceTest {
    @Autowired
    InventoryService inventoryService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    IdempotencyRepository idempotencyRepository;
    @MockitoBean
    StockLedgerService stockLedgerService;
    @MockitoSpyBean
    IdempotencyRepository idemSpy; // save에서 유니크 경합을 테스트 하기 위해 사용, DataIntegrityViolationException 상황
    @Autowired
    TransactionTemplate tx; // DB LOCK 테스트


    @Test
    void decreaseByOrderTest() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 20;
        String requestId = "test";

        //when
        StockResult result = inventoryService.decreaseByOrder(productId, orderId, quantity, requestId);
        Product findProduct = productRepository.findById(productId).orElseThrow();

        //then
        assertThat(result.getRemainingStock()).isEqualTo(findProduct.getStock());
    }

    @Test
    void decreaseByOrderTest_idempotency() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 20;
        String requestId = "test";

        //when
        StockResult firstResult = inventoryService.decreaseByOrder(productId, orderId, quantity, requestId);
        StockResult secondResult = inventoryService.decreaseByOrder(productId, orderId, quantity, requestId);

        //then
        assertThat(firstResult).isEqualTo(secondResult);
    }

    @Test
    void decreaseByOrderTest_차감실패() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 200;
        String requestId = "test";


        assertThatThrownBy(
            ()-> inventoryService.decreaseByOrder(productId, orderId, quantity, requestId))
            .isInstanceOf(InsufficientStockException.class);


    }


    @Test
    void increaseByOrderTest() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 20;
        String requestId = "test";

        //when
        StockResult result = inventoryService.increaseByOrder(productId, orderId, quantity, requestId);
        Product findProduct = productRepository.findById(productId).orElseThrow();

        //then
        assertThat(result.getRemainingStock()).isEqualTo(findProduct.getStock());
    }

    @Test
    void increaseByOrderTest_idempotency() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 20;
        String requestId = "test";

        //when
        StockResult firstResult = inventoryService.increaseByOrder(productId, orderId, quantity, requestId);
        StockResult secondResult = inventoryService.increaseByOrder(productId, orderId, quantity, requestId);

        //then
        assertThat(firstResult).isEqualTo(secondResult);
    }


    // -- decreaseByOrderV2 테스트
    @Test
    void 성공_재고차감_원장저장_멱등저장() {
        // given
        Long productId = 1L;
        Long orderId = 1L;
        int qty = 3;
        String requestId = "TEST-1";

        // when
        StockResult result = inventoryService.decreaseByOrderV2(productId, orderId, qty, requestId);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRemainingStock()).isEqualTo(97);

        // DB 반영 확인
        Product after = productRepository.findById(productId).orElseThrow();
        assertThat(after.getStock()).isEqualTo(97);

        // 원장 호출 확인
        verify(stockLedgerService, times(1))
            .save(eq(productId), eq(Direction.OUT), eq(qty), eq(orderId), eq(requestId));

        // 멱등 저장 확인
        IdempotencyRecord rec = idempotencyRepository.findByRequestId(requestId).orElseThrow();
        assertThat(rec.getStatus()).isEqualTo(IdempotencyStatus.SUCCESS);
        assertThat(rec.getRemainingStock()).isEqualTo(97);
    }

    @Test
    void 멱등캐시_히트시_바로반환_원장호출없음() {
        // given
        Long productId = 1L;
        Long orderId = 2L;
        int qty = 5;
        String requestId = "TEST-2";

        // 미리 성공 레코드 저장 (성공 재사용 시나리오)
        IdempotencyRecord rec = IdempotencyRecord.create(requestId, IdempotencyStatus.SUCCESS, "OK", 95);
        idempotencyRepository.save(rec);

        // when
        StockResult result = inventoryService.decreaseByOrderV2(productId, orderId, qty, requestId);

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRemainingStock()).isEqualTo(95);

        // 원장 호출 없음
        verify(stockLedgerService, never())
            .save(anyLong(), any(), anyInt(), anyLong(), anyString());

        // 재고 변화 없음
        Product after = productRepository.findById(productId).orElseThrow();
        assertThat(after.getStock()).isEqualTo(100);
    }


    @Test
    void 멱등저장_경합시_기존레코드_재사용() {
        // given
        Long productId = 1L;
        Long orderId = 3L;
        int qty = 5;
        String requestId = "TEST-3";

        // 첫 조회 시에는 비어있다가, save 시점에 유니크 예외를 던지고,
        // 이후 findByRequestId는 기존 레코드를 반환하도록 스텁
        IdempotencyRecord existing = IdempotencyRecord.create(requestId, IdempotencyStatus.SUCCESS, "OK", 45);

        // findByRequestId: 1차(초입)에는 empty, 2차(예외 후 재조회)는 present
        when(idemSpy.findByRequestId(requestId))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));

        // save는 경합으로 예외를 던지도록
        doThrow(new DataIntegrityViolationException("dup"))
            .when(idemSpy).save(any(IdempotencyRecord.class));

        // when
        StockResult result = inventoryService.decreaseByOrderV2(productId, orderId, qty, requestId);

        // then: 기존 레코드 재사용
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRemainingStock()).isEqualTo(45);

        // 원장 호출은 1회 (비즈니스 성공 경로)
        verify(stockLedgerService, times(1))
            .save(eq(productId), eq(Direction.OUT), eq(qty), eq(orderId), eq(requestId));
    }

    @Test
    void 재고부족시_예외_발생_원장_멱등저장_없음() {
        // given
        Long productId = 1L;
        Long orderId = 4L;
        int qty = 500;
        String requestId = "TEST-4";

        // when / then
        assertThatThrownBy(() ->
            inventoryService.decreaseByOrderV2(productId, orderId, qty, requestId)
        ).isInstanceOf(InsufficientStockException.class);

        // 재고 변경 없음
        Product after = productRepository.findById(productId).orElseThrow();
        assertThat(after.getStock()).isEqualTo(100);

        // 원장 호출 없음
        verify(stockLedgerService, never())
            .save(anyLong(), any(), anyInt(), anyLong(), anyString());

        // 멱등 저장 없음 (정책: 실패는 멱등 테이블에 저장하지 않음)
        assertThat(idempotencyRepository.findByRequestId(requestId)).isEmpty();
    }


    @Test
    void 디비_락_쿼리_힌트_타임아웃_테스트() throws Exception {
        Long productId = 1L;

        CountDownLatch latch = new CountDownLatch(1);

        // TX1: 락 잡고 유지
        Thread t1 = new Thread(() -> tx.execute(status -> {
            Product p = productRepository.findForUpdateWithTimeout(productId); // 락 시작
            latch.countDown(); // TX2 시작 허용
            try {
                sleep(3000);       // 잠깐 홀드해서 TX2가 기다리게
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            p.decreaseStock(1);
            return null;       // 커밋 → 락 해제
        }));

        // TX2: 같은 로우 잠금 시도 → 타임아웃 기대
        AtomicReference<Exception> exRef = new AtomicReference<>();
        Thread t2 = new Thread(() -> {
            try {
                latch.await();
                tx.execute(status -> {
                    productRepository.findForUpdateWithTimeout(productId);
                    return null;
                });
            } catch (Exception e) {
                exRef.set(e);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(exRef.get()).isInstanceOfAny(LockTimeoutException.class, PessimisticLockingFailureException.class);
    }


}