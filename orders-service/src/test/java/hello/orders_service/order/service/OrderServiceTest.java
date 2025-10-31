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
}