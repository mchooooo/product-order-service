package hello.orders_service.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.exception.OrderStateException;
import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.outbox.OutboxStatus;
import hello.orders_service.order.repository.OrderOutboxRepository;
import hello.orders_service.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderRecoveryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxRepository orderOutboxRepository;

    @Mock
    private OrderOutboxService orderOutboxService;

    private OrderRecoveryService orderRecoveryService;

    @BeforeEach
    void setUp() {
        orderRecoveryService = new OrderRecoveryService(
            orderRepository,
            orderOutboxRepository,
            new ObjectMapper(),
            orderOutboxService
        );
    }

    @Test
    void 실패_주문_재처리_시_pending_복귀와_outbox_생성() {
        Order order = Order.create(10L, "buyer", 2);
        ReflectionTestUtils.setField(order, "id", 77L);
        order.failStatus("OUTBOX_RETRY_EXHAUSTED");

        given(orderRepository.findById(77L)).willReturn(Optional.of(order));

        orderRecoveryService.retryFailedOrder(77L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getFailReason()).isNull();

        ArgumentCaptor<OrderOutbox> captor = ArgumentCaptor.forClass(OrderOutbox.class);
        verify(orderOutboxRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(77L);
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void 실패가_아닌_주문은_재처리할_수_없다() {
        Order order = Order.create(10L, "buyer", 2);
        ReflectionTestUtils.setField(order, "id", 77L);

        given(orderRepository.findById(77L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderRecoveryService.retryFailedOrder(77L))
            .isInstanceOf(OrderStateException.class);

        verify(orderOutboxRepository, never()).save(any());
    }

    @Test
    void 실패_outbox는_재발행_대기로_되돌린다() {
        OrderOutbox outbox = OrderOutbox.pending(77L, "STOCK_DECREASE_REQUEST", "{\"orderId\":77}");
        ReflectionTestUtils.setField(outbox, "id", 5L);
        ReflectionTestUtils.setField(outbox, "status", OutboxStatus.FAILED);

        given(orderOutboxRepository.findById(5L)).willReturn(Optional.of(outbox));

        OrderOutbox result = orderRecoveryService.requeueOutbox(5L);

        verify(orderOutboxService).requeue(5L);
        assertThat(result).isSameAs(outbox);
    }
}
