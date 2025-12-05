package hello.orders_service.order.saga.step;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.order.service.OrderService;
import hello.orders_service.saga.SagaErrorType;
import hello.orders_service.saga.SagaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelOrderStepTest {
    @Mock
    OrderService orderService;
    CancelOrderStep step;

    @BeforeEach
    void init() {
        this.step = new CancelOrderStep(orderService);
    }

    private void setOrderId(Order order) throws NoSuchFieldException, IllegalAccessException {
        Field id = order.getClass().getDeclaredField("id");
        id.setAccessible(true);
        id.set(order, 1L);
    }

    @Test
    void execute_성공시_cancelOrder_호출되고_ctx_order가_변경된다() throws NoSuchFieldException, IllegalAccessException {
        // given
        OrderSagaContext ctx = new OrderSagaContext();
        ctx.setOrderId(1L);

        Order canceled = new Order();
        setOrderId(canceled);

        when(orderService.cancelOrder(1L)).thenReturn(canceled);

        // when
        step.execute(ctx);

        // then
        verify(orderService).cancelOrder(1L);
        assertThat(ctx.getOrder()).isEqualTo(canceled);
    }

    @Test
    void cancelOrder에서_RuntimeException_발생시_SagaException_COMPENSATABLE_던진다() {
        // given
        OrderSagaContext ctx = new OrderSagaContext();
        ctx.setOrderId(1L);

        when(orderService.cancelOrder(eq(1L)))
            .thenThrow(new RuntimeException("DB error"));

        // when
        SagaException ex = assertThrows(
            SagaException.class,
            () -> step.execute(ctx)
        );

        // then
        assertThat(ex.getType()).isEqualTo(SagaErrorType.COMPENSATE);
        assertThat(ex.getMessage()).contains("CancelOrderStep failed");
    }

    @Test
    void compensate는_NO_OP() {
        // given
        OrderSagaContext ctx = new OrderSagaContext();
        ctx.setOrderId(1L);

        // when
        step.compensate(ctx);

        // then
        // compensate 에서는 아무런 호출이 없어야 한다는 의미로 verifyNoMoreInteractions
        verifyNoInteractions(orderService);
    }

}