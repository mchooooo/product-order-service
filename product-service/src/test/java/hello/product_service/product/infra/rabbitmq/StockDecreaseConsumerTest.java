package hello.product_service.product.infra.rabbitmq;

import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.model.event.StockDecreaseEvent;
import hello.product_service.product.model.event.StockResultEvent;
import hello.product_service.product.service.InventoryServiceV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockDecreaseConsumerTest {

    @Mock
    private InventoryServiceV2 inventoryService;

    @Mock
    private StockResultProducer stockResultProducer;

    @InjectMocks
    private StockDecreaseConsumer stockDecreaseConsumer;

    @Test
    void 재고부족은_실패이벤트를_보내고_dlq로_보내지_않는다() {
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 2L, 3, "DEC-1");
        willThrow(new InsufficientStockException(2L, 0)).given(inventoryService)
            .decreaseByOrder(2L, 1L, 3, "DEC-1");

        stockDecreaseConsumer.handleStockDecrease(event);

        ArgumentCaptor<StockResultEvent> captor = ArgumentCaptor.forClass(StockResultEvent.class);
        verify(stockResultProducer).sendResult(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void 시스템오류는_실패이벤트를_보내고_dlq로_보낸다() {
        StockDecreaseEvent event = new StockDecreaseEvent(1L, 2L, 3, "DEC-1");
        willThrow(new RuntimeException("boom")).given(inventoryService)
            .decreaseByOrder(2L, 1L, 3, "DEC-1");

        assertThatThrownBy(() -> stockDecreaseConsumer.handleStockDecrease(event))
            .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        ArgumentCaptor<StockResultEvent> captor = ArgumentCaptor.forClass(StockResultEvent.class);
        verify(stockResultProducer).sendResult(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("SYSTEM_ERROR");
    }
}
