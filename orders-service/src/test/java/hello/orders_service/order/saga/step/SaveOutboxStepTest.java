package hello.orders_service.order.saga.step;

import com.fasterxml.jackson.databind.ObjectMapper;
import hello.orders_service.order.infra.messaging.event.StockDecreaseRequestEvent;
import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.outbox.OutboxStatus;
import hello.orders_service.order.repository.OrderOutboxRepository;
import hello.orders_service.order.saga.OrderSagaContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaveOutboxStepTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OrderOutboxRepository orderOutboxRepository;

    private SaveOutboxStep saveOutboxStep;

    @BeforeEach
    void setUp() {
        this.saveOutboxStep = new SaveOutboxStep(orderOutboxRepository, objectMapper);
    }

    @Test
    void outbox_저장_내용_검증() throws Exception {
        // given
        OrderSagaContext context = new OrderSagaContext();
        context.setOrderId(100L);
        context.setProductId(500L);
        context.setQuantity(2);
        context.setDecKey("DEC-123");

        // when
        saveOutboxStep.execute(context);

        // then
        ArgumentCaptor<OrderOutbox> captor = ArgumentCaptor.forClass(OrderOutbox.class);
        verify(orderOutboxRepository, times(1)).save(captor.capture());

        OrderOutbox saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(100L);
        assertThat(saved.getEventType()).isEqualTo("STOCK_DECREASE_REQUEST");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);

        StockDecreaseRequestEvent event =
            objectMapper.readValue(saved.getPayload(), StockDecreaseRequestEvent.class);
        assertThat(event.getOrderId()).isEqualTo(100L);
        assertThat(event.getProductId()).isEqualTo(500L);
        assertThat(event.getQuantity()).isEqualTo(2);
        assertThat(event.getRequestId()).isEqualTo("DEC-123");
    }
}

