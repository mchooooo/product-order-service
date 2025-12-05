package hello.orders_service.order.saga.step;

import hello.orders_service.common.ApiSuccess;
import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockResult;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.exception.ApiException;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.ErrorCode;
import hello.orders_service.order.exception.client.ProductClientException;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.order.service.OrderService;
import hello.orders_service.saga.SagaErrorType;
import hello.orders_service.saga.SagaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecreaseStockAndConfirmStepTest {

    @Mock
    ProductClient productClient;
    @Mock
    OrderService orderService;

    DecreaseStockAndConfirmStep step;

    @BeforeEach
    void init() {
        this.step = new DecreaseStockAndConfirmStep(productClient, orderService);
    }

    @Test
    void 성공_재고차감_후_주문확정() {
        // given
        OrderSagaContext ctx = new OrderSagaContext();
        ctx.setOrderId(1L);
        ctx.setProductId(10L);
        ctx.setQuantity(2);
        ctx.setDecKey("DEC-1");

        Order confirmed = new Order();
        when(productClient.decreaseByOrder(anyLong(), any(), any()))
            .thenReturn(ApiSuccess.of(new StockResult(true, 10, "OK"), null));
        when(orderService.confirmOrder(1L)).thenReturn(confirmed);

        // when
        step.execute(ctx);

        // then
        verify(productClient).decreaseByOrder(eq(10L), any(), eq("DEC-1"));
        verify(orderService).confirmOrder(1L);
        assertThat(ctx.getOrder()).isEqualTo(confirmed);
    }

    @Test
    void 상품서버_4xx_failOrder_호출_SagaException_BUSINESS() {
        // given
        OrderSagaContext ctx = new OrderSagaContext();
        ctx.setOrderId(1L);
        ctx.setProductId(10L);
        ctx.setQuantity(2);
        ctx.setDecKey("DEC-1");

        Order failOrder = new Order();
        failOrder.failStatus("test");

        when(productClient.decreaseByOrder(anyLong(), any(), any()))
            .thenThrow(new ProductClientException(ErrorCode.INSUFFICIENT_STOCK, "INSUFFICIENT_STOCK", null));
        when(orderService.failOrder(anyLong(), any()))
            .thenReturn(failOrder);

        // when
        SagaException ex = assertThrows(
            SagaException.class,
            () -> step.execute(ctx)
        );

        // then
        assertThat(ex.getType()).isEqualTo(SagaErrorType.BUSINESS);
        verify(orderService).failOrder(eq(1L), contains("INSUFFICIENT_STOCK"));
        // ctx.getOrder()가 failOrder로 세팅되었는지까지 보면 더 좋음
        assertThat(ctx.getOrder().getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void 상품서버_5xx_SagaException_RETRYABLE() {
        // given
        OrderSagaContext ctx = basicCtx();

        when(productClient.decreaseByOrder(anyLong(), any(), any()))
            .thenThrow(new DependencyFailedException(ErrorCode.DEPENDENCY_FAILED, "DEPENDENCY_FAILED", null, null));

        // when
        SagaException ex = assertThrows(
            SagaException.class,
            () -> step.execute(ctx)
        );

        // then
        assertThat(ex.getType()).isEqualTo(SagaErrorType.RETRYABLE);
        verify(orderService, never()).failOrder(anyLong(), anyString());
    }

    private OrderSagaContext basicCtx() {
        OrderSagaContext ctx = new OrderSagaContext();
        ctx.setOrderId(1L);
        ctx.setProductId(10L);
        ctx.setQuantity(2);
        ctx.setDecKey("DEC-1");
        ctx.setIncKey("INC-1");
        return ctx;
    }

    @Test
    void 로컬예외_SagaException_COMPENSATABLE() {
        // given
        OrderSagaContext ctx = basicCtx();

        // 재고는 줄이는데 confirmOrder에서 로컬 예외 발생
        when(productClient.decreaseByOrder(anyLong(), any(), any()))
            .thenReturn(ApiSuccess.of(new StockResult(true, 1, "OK"), null));
        when(orderService.confirmOrder(anyLong()))
            .thenThrow(new ApiException(ErrorCode.ORDER_NOT_FOUND, "ORDER_NOT_FOUND"));

        // when
        SagaException ex = assertThrows(
            SagaException.class,
            () -> step.execute(ctx)
        );

        // then
        assertThat(ex.getType()).isEqualTo(SagaErrorType.COMPENSATE);
    }

    @Test
    void compensate_재고_INC_후_failOrder_COMPENSATED() {
        // given
        OrderSagaContext ctx = basicCtx();
        ctx.setIncKey("INC-1");

        // when
        step.compensate(ctx);

        // then
        verify(productClient).increaseByOrder(eq(10L), any(), eq("INC-1"));
        verify(orderService).failOrder(eq(1L), eq("COMPENSATED"));
    }

}