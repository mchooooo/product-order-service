package hello.product_service.product.infra.rabbitmq;

import hello.product_service.product.infra.TestContainerInitializer;
import hello.product_service.product.infra.redis.StockRedisManager;
import hello.product_service.product.model.event.StockDecreaseEvent;
import hello.product_service.product.model.event.StockResultEvent;
import hello.product_service.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ContextConfiguration(initializers = TestContainerInitializer.class)
class StockDecreaseConsumerTest {

    @Autowired
    private StockDecreaseConsumer stockDecreaseConsumer;

    @Autowired
    private StockRedisManager stockRedisManager; // Redis 조작용

    @Autowired
    private ProductRepository productRepository; // DB 확인용

    @MockitoBean
    private StockResultProducer resultProducer; // 응답 발송은 Mock 처리하여 캡처

    private final Long TEST_PRODUCT_ID = 1L;

    @BeforeEach
    void setUp() {
        // 테스트 전 재고 초기화
        // DB에도 해당 상품이 100개 있다고 가정
        stockRedisManager.initializeStock(TEST_PRODUCT_ID, 100);
    }

    @Test
    void handleStockDecrease_성공() {
        // given
        StockDecreaseEvent event = new StockDecreaseEvent(101L, TEST_PRODUCT_ID, 5, "REQ_SUCCESS_01");

        // when
        stockDecreaseConsumer.handleStockDecrease(event);

        // then
        assertThat(stockRedisManager.findStock(TEST_PRODUCT_ID)).isEqualTo(95);

        ArgumentCaptor<StockResultEvent> captor = ArgumentCaptor.forClass(StockResultEvent.class);
        verify(resultProducer).sendResult(captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
    }

    @Test
    void handleStockDecrease_실패_레디스_재고_복구() {
        // given
        // DB에 없는 상품 ID를 사용하여 의도적으로 DB 예외 유도
        Long invalidProductId = 9999L;
        int decreaseQuantity = 10;
        StockDecreaseEvent failEvent = new StockDecreaseEvent(102L, invalidProductId, decreaseQuantity, "REQ_FAIL_01");
        //  DB 예외를 내기 위해서 invalidProductId 레디스에 등록,
        stockRedisManager.initializeStock(invalidProductId, 100);

        // when
        stockDecreaseConsumer.handleStockDecrease(failEvent);

        // then
        // Redis 재고 확인: 차감되었다가 복구되어 100이어야 함
        assertThat(stockRedisManager.findStock(invalidProductId)).isEqualTo(100);

        // 결과 메시지 캡처 및 검증
        ArgumentCaptor<StockResultEvent> captor = ArgumentCaptor.forClass(StockResultEvent.class);
        verify(resultProducer).sendResult(captor.capture());
        assertThat(captor.getValue().isSuccess()).isFalse();
        assertThat(captor.getValue().getReason()).isEqualTo("SYSTEM_ERROR");
    }

    @Test
    void handleStockDecrease_멱등성_체크_중복_차감_없음() {
        // given
        StockDecreaseEvent event = new StockDecreaseEvent(103L, TEST_PRODUCT_ID, 5, "REQ_IDEM_01");

        // when: 동일한 요청 두 번 발송
        stockDecreaseConsumer.handleStockDecrease(event);
        stockDecreaseConsumer.handleStockDecrease(event);

        // then: 재고는 한 번만 깎여서 95여야 함
        assertThat(stockRedisManager.findStock(TEST_PRODUCT_ID)).isEqualTo(95);
    }
}
