package hello.orders_service.order.saga.step;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderPendingStepTest {
    @Mock
    OrderService orderService;

    CreateOrderPendingStep step;

    @BeforeEach
    void init() {
        step = new CreateOrderPendingStep(orderService);
    }

    private void setOrderId(Order order) throws NoSuchFieldException, IllegalAccessException {
        Field id = order.getClass().getDeclaredField("id");
        id.setAccessible(true);
        id.set(order, 1L);
    }

    @Test
    void execute_호출시_createOrderPending_호출_ctx_세팅() throws NoSuchFieldException, IllegalAccessException {
        // given
        OrderSagaContext ctx = new OrderSagaContext();
        ctx.setProductId(10L);
        ctx.setBuyerId("buyer-1");
        ctx.setQuantity(3);

        Order pending = new Order();
        // 테스트 편의를 위해 ID만 세팅
        // 리플렉션으로 주입
        setOrderId(pending);

        when(orderService.createOrderPending(10L, "buyer-1", 3))
            .thenReturn(pending);

        // when
        step.execute(ctx);

        // then
        verify(orderService).createOrderPending(10L, "buyer-1", 3);
        assertThat(ctx.getOrder()).isEqualTo(pending);
        assertThat(ctx.getOrderId()).isEqualTo(1L);
        assertThat(ctx.getDecKey()).isEqualTo("DEC-1");
        assertThat(ctx.getIncKey()).isEqualTo("INC-1");
    }

    @Test
    void compensate는_NO_OP() {
        // given
        OrderSagaContext ctx = new OrderSagaContext();

        // when
        step.compensate(ctx);

        // then
        verifyNoInteractions(orderService);
    }
}