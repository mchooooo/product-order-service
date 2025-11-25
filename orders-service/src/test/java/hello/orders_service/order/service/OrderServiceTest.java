package hello.orders_service.order.service;

import feign.FeignException;
import hello.orders_service.common.ApiSuccess;
import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.client.dto.StockResult;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderService orderService;

    @MockitoBean
    ProductClient productClient;


    private void stubbingDecreaseByOrder(StockResult stockResult) {
        when(productClient.decreaseByOrder(anyLong(),
            any(StockAdjustByOrderRequest.class),
            anyString())
        ).thenReturn(ApiSuccess.of(stockResult, null));

    }

    private void stubbingClientThrowException() {
        when(productClient.decreaseByOrder(anyLong(), any(StockAdjustByOrderRequest.class), anyString()))
            .thenThrow(FeignException.class);
    }

    @Test
    void create_success() {
        //given
        StockResult stockResult = new StockResult();
        stockResult.setSuccess(true);
        stockResult.setRemainingStock(1);
        stockResult.setMessage("test");

        stubbingDecreaseByOrder(stockResult);


        //when
        Order order = orderService.create(1L, "u-001", 1);

        //then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void create_fail() {
        // given
        stubbingClientThrowException();

        // when & then
        assertThatThrownBy(() -> orderService.create(1L, "u-001", 1))
            .isInstanceOf(DependencyFailedException.class);

    }

    @Test
    void create_order_pending_test() {
        //given
        //when
        Order order = orderService.createOrderPending(1L, "test", 1);
        List<Order> orders = orderRepository.findAll();

        //then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        //테스트 데이터 2개는 삽입되어있고 하나 추가해서 3이 나와야한다.
        assertThat(orders.size()).isEqualTo(3);

    }

    @Test
    void confirm_order_test() {
        //given
        Order order = orderService.createOrderPending(1L, "test", 10);

        //when
        Order confirmOrder = orderService.confirmOrder(order.getId());

        //then
        assertThat(confirmOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void fail_order_test() {
        //given
        Order order = orderService.createOrderPending(1L, "test", 10);

        //when
        Order failOrder = orderService.failOrder(order.getId(), "test fail");

        //then
        assertThat(failOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(failOrder.getFailReason()).isEqualTo("test fail");
    }

    @Test
    void cancel_order_test() {
        //given
        Order order = orderService.createOrderPending(1L, "test", 10);

        //when
        Order cancelOrder = orderService.cancelOrder(order.getId());

        //then
        assertThat(cancelOrder.getStatus()).isEqualTo(OrderStatus.CANCEL);
    }
}