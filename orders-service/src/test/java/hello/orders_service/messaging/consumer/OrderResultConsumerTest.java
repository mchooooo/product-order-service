package hello.orders_service.messaging.consumer;

import hello.orders_service.messaging.event.StockResultEvent;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.repository.OrderRepository;
import hello.orders_service.order.saga.step.CreateOrderPendingStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderResultConsumerTest {
    @Mock
    OrderRepository orderRepository;
    @Mock
    CreateOrderPendingStep createOrderPendingStep;
    @InjectMocks
    OrderResultConsumer orderResultConsumer;

    @Test
    void message_성공_수신_후_주문상태_CONFIRM_테스트 () throws Exception {
        // given
        Long orderId = 3L;
        Order order = Order.create(1L, "test", 3);
        StockResultEvent event = new StockResultEvent(orderId, true, "Success");

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        orderResultConsumer.handleOrderResult(event);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(createOrderPendingStep, never()).compensate(any());
    }

    @Test
    void message_실패_수신_후_보상_실행_테스트 () throws Exception {
        // given
        Long orderId = 3L;
        Order order = Order.create(1L, "test", 3);
        StockResultEvent event = new StockResultEvent(orderId, false, "Out of stock");

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when
        orderResultConsumer.handleOrderResult(event);

        // then
        verify(createOrderPendingStep, times(1)).compensate(any());
    }
}