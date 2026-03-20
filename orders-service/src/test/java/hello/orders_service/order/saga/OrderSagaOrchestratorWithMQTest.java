package hello.orders_service.order.saga;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.saga.step.CreateOrderPendingStep;
import hello.orders_service.order.saga.step.SaveOutboxStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorWithMQTest {

    @Mock
    private CreateOrderPendingStep createOrderPendingStep;

    @Mock
    private SaveOutboxStep saveOutboxStep;

    @Test
    void v3_주문생성_후_아웃박스저장_호출() {
        // given
        OrderSagaOrchestratorWithMQ orchestrator =
            new OrderSagaOrchestratorWithMQ(createOrderPendingStep, saveOutboxStep);

        Order order = new Order();
        Long orderId = 123L;

        doAnswer(invocation -> {
            OrderSagaContext ctx = invocation.getArgument(0);
            ctx.setOrder(order);
            ctx.setOrderId(orderId);
            ctx.setDecKey("DEC-" + orderId);
            ctx.setIncKey("INC-" + orderId);
            return null;
        }).when(createOrderPendingStep).execute(any(OrderSagaContext.class));

        // when
        Order result = orchestrator.runSaga(500L, "buyer-1", 2);

        // then
        InOrder inOrder = inOrder(createOrderPendingStep, saveOutboxStep);
        inOrder.verify(createOrderPendingStep, times(1)).execute(any(OrderSagaContext.class));
        inOrder.verify(saveOutboxStep, times(1)).execute(any(OrderSagaContext.class));

        assertThat(result).isSameAs(order);
    }
}

