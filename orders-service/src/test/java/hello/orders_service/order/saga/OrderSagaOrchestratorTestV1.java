package hello.orders_service.order.saga;

import hello.orders_service.common.ApiSuccess;
import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.client.dto.StockResult;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.exception.ApiException;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.ErrorCode;
import hello.orders_service.order.exception.client.ProductClientException;
import hello.orders_service.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class OrderSagaOrchestratorTestV1 {
    @Mock
    ProductClient productClient;
    @Mock
    OrderService orderService;

    @InjectMocks
    OrderSagaOrchestrator orchestrator;

    private void setOrderId(Order order) throws NoSuchFieldException, IllegalAccessException {
        Field id = order.getClass().getDeclaredField("id");
        id.setAccessible(true);
        id.set(order, 1L);
    }

    private void setConfirm(Order order) throws NoSuchFieldException, IllegalAccessException {
        Field status = order.getClass().getDeclaredField("status");
        status.setAccessible(true);
        status.set(order, OrderStatus.CONFIRMED);
    }

    private void setFail(Order order) throws NoSuchFieldException, IllegalAccessException {
        Field status = order.getClass().getDeclaredField("status");
        status.setAccessible(true);
        status.set(order, OrderStatus.FAILED);
    }

    private void setCancel(Order order) throws NoSuchFieldException, IllegalAccessException {
        Field status = order.getClass().getDeclaredField("status");
        status.setAccessible(true);
        status.set(order, OrderStatus.CANCEL);
    }

    // ---------- 주문 생성 사가 테스트 ----------

    @Test
    void startOrder_success_then_confirm() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order order = Order.create(10L, "test", 1);
        setOrderId(order);

        StockResult stockResult = new StockResult(true, 97, "OK");
        when(productClient.decreaseByOrder(anyLong(), any(StockAdjustByOrderRequest.class), startsWith("DEC-")))
            .thenReturn(new ApiSuccess<>(stockResult, null));

        setConfirm(order);
        when(orderService.confirmOrder(anyLong())).thenReturn(order);

        when(orderService.createOrderPending(anyLong(), anyString(), anyInt())).thenReturn(order);

        // when
        Order result = orchestrator.startOrderV1(10L, "buyer", 3);

        // then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderService).createOrderPending(10L, "buyer", 3);
        verify(productClient).decreaseByOrder(anyLong(), any(StockAdjustByOrderRequest.class), startsWith("DEC-"));
        verify(orderService).confirmOrder(anyLong());
        // 보상 호출 없음
        verify(productClient, never()).increaseByOrder(anyLong(), any(), anyString());
    }

    @Test
    void startOrder_4xx_then_fail_without_compensation() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order order = Order.create(10L, "test", 1);
        setOrderId(order);

        when(orderService.createOrderPending(anyLong(), anyString(), anyInt())).thenReturn(order);

        when(productClient.decreaseByOrder(anyLong(), any(), anyString()))
            .thenThrow(new ProductClientException(ErrorCode.INSUFFICIENT_STOCK, "재고 부족", null));


        setFail(order);
        when(orderService.failOrder(anyLong(), anyString())).thenReturn(order);

        // when
        Order result = orchestrator.startOrderV1(10L, "buyer", 5);

        // then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderService).failOrder(anyLong(), anyString());
        // 보상 호출 금지
        verify(productClient, never()).increaseByOrder(anyLong(), any(), anyString());
    }

    @Test
    void startOrder_dependency5xx_then_mark_failed_and_rethrow() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order order = Order.create(10L, "test", 1);
        setOrderId(order);
        when(orderService.createOrderPending(anyLong(), anyString(), anyInt())).thenReturn(order);

        when(productClient.decreaseByOrder(anyLong(), any(), anyString()))
            .thenThrow(new DependencyFailedException(ErrorCode.DEPENDENCY_FAILED, "upstream failed", null, null));


        // when / then
        assertThatThrownBy(() -> orchestrator.startOrderV1(10L, "buyer", 2))
            .isInstanceOf(DependencyFailedException.class);


        // 보상 호출 금지 (재시도 대상이므로)
        verify(productClient, never()).increaseByOrder(anyLong(), any(), anyString());
    }

    @Test
    void startOrder_localException_then_compensate_and_fail_compensated() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order order = Order.create(10L, "test", 1);
        setOrderId(order);
        when(orderService.createOrderPending(10L, "buyer", 3)).thenReturn(order);

        StockResult stockResult = new StockResult(true, 97, "OK");
        when(productClient.decreaseByOrder(anyLong(), any(), anyString()))
            .thenReturn(new ApiSuccess<>(stockResult, null));

        // confirm 단계에서 로컬 예외 발생
        when(orderService.confirmOrder(1L)).thenThrow(new ApiException(null, "local fail", null));

        setFail(order);
        when(orderService.failOrder(anyLong(), eq("COMPENSATED"))).thenReturn(order);

        // when
        Order result = orchestrator.startOrderV1(10L, "buyer", 3);

        // then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        // 보상(increase) 호출이 일어났는지 확인
        verify(productClient).increaseByOrder(anyLong(), any(StockAdjustByOrderRequest.class), eq("INC-1"));
        verify(orderService).failOrder(anyLong(), eq("COMPENSATED"));
    }


    // ---------- 주문 취소 사가 테스트 ----------

    @Test
    void startCancel_success_then_cancelled() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order order = Order.create(10L, "test", 1);
        setOrderId(order);
        setConfirm(order);
        when(orderService.findById(anyLong())).thenReturn(order);

        Order cancelOrder = Order.create(10L, "teset", 1);
        setOrderId(cancelOrder);
        setCancel(cancelOrder);
        when(orderService.cancelOrder(anyLong())).thenReturn(cancelOrder);


        // product increase 성공
        when(productClient.increaseByOrder(anyLong(), any(), anyString()))
            .thenReturn(new ApiSuccess<>(new StockResult(true, 100, "OK"), null));

        // when
        Order result = orchestrator.startCancelV1(anyLong());

        // then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCEL);
        verify(productClient).increaseByOrder(eq(10L), any(StockAdjustByOrderRequest.class), anyString());
        verify(orderService).cancelOrder(1L);
    }

    @Test
    void startCancel_dependency5xx_then_rethrow_and_keep_confirmed() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order order = Order.create(10L, "test", 1);
        setOrderId(order);
        setConfirm(order);
        when(orderService.findById(anyLong())).thenReturn(order);

        when(productClient.increaseByOrder(anyLong(), any(), anyString()))
            .thenThrow(new DependencyFailedException(null, "upstream", null, null));

        // when / then
        assertThatThrownBy(() -> orchestrator.startCancelV1(anyLong()))
            .isInstanceOf(DependencyFailedException.class);

        // 상태 전이 없음
        verify(orderService, never()).cancelOrder(anyLong());
    }


    @Test
    void startCancel_business4xx_then_keep_confirmed() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order order = Order.create(10L, "test", 1);
        setOrderId(order);
        setConfirm(order);
        when(orderService.findById(1L)).thenReturn(order);

        when(productClient.increaseByOrder(anyLong(), any(), anyString()))
            .thenThrow(new ProductClientException(ErrorCode.PRODUCT_NOT_FOUND, "not found", null));

        // when
        Order result = orchestrator.startCancelV1(1L);

        // then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderService, never()).cancelOrder(anyLong());
    }


    @Test
    void startCancel_localException_then_compensate_decrease_and_keep_confirmed() throws NoSuchFieldException, IllegalAccessException {
        // given
        Order order = Order.create(10L, "test", 1);
        setOrderId(order);
        setConfirm(order);
        when(orderService.findById(1L)).thenReturn(order);

        // increase는 성공했는데 cancelOrder에서 로컬 예외
        when(productClient.increaseByOrder(anyLong(), any(), anyString()))
            .thenReturn(new ApiSuccess<>(new StockResult(true, 102, "OK"), null));
        when(orderService.cancelOrder(1L)).thenThrow(new ApiException(null, "cancel fail", null));

        // when
        Order result = orchestrator.startCancelV1(1L);

        // then
        // 보상(decrease) 호출이 일어났는지 확인
        verify(productClient).decreaseByOrder(eq(10L), any(StockAdjustByOrderRequest.class), eq("DEC-1"));
        // 상태는 CONFIRMED 유지
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

}