package hello.orders_service.order.saga.step;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.repository.OrderRepository;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderPendingStepTest {
    @Mock
    OrderService orderService;
    @Mock
    OrderRepository orderRepository;

    CreateOrderPendingStep step;

    @BeforeEach
    void init() {
        step = new CreateOrderPendingStep(orderService, orderRepository);
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
    void compensate는_주문상태_FAILED_갱신() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order pending = Order.create(1L, "test", 3);
        setOrderId(pending);
        OrderSagaContext ctx = OrderSagaContext.from(pending);
        given(orderRepository.findById(pending.getId())).willReturn(Optional.of(pending));

        // when
        step.compensate(ctx);

        // then
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.FAILED);
    }
}