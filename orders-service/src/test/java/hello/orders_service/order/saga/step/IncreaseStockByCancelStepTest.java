package hello.orders_service.order.saga.step;

import hello.orders_service.common.ApiSuccess;
import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.client.dto.StockResult;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.ErrorCode;
import hello.orders_service.order.exception.client.ProductClientException;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.saga.SagaErrorType;
import hello.orders_service.saga.SagaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncreaseStockByCancelStepTest {

    @Mock
    ProductClient productClient;

    IncreaseStockByCancelStep step;

    @BeforeEach
    void init() {
        this.step = new IncreaseStockByCancelStep(productClient);
    }

    private OrderSagaContext basicCtx() {
        OrderSagaContext ctx = new OrderSagaContext();
        ctx.setOrderId(1L);
        ctx.setProductId(10L);
        ctx.setBuyerId("test");
        ctx.setQuantity(10);
        ctx.setDecKey("DEC-1");
        ctx.setIncKey("INC-1");

        return ctx;
    }

    @Test
    void execute_성공시_increaseByOrder_한번_호출되고_예외없음() {
        // given
        OrderSagaContext ctx = basicCtx();

        when(productClient.increaseByOrder(anyLong(), any(), any()))
            .thenReturn(ApiSuccess.of(new StockResult(true, 10, "OK"), null));
        // when
        step.execute(ctx);

        // then
        verify(productClient).increaseByOrder(eq(10L), any(), eq("INC-1"));
    }

    @Test
    void 상품서버_4xx_SagaException_BUSINESS_던진다() {
        // given
        OrderSagaContext ctx = basicCtx();

        doThrow(new ProductClientException(ErrorCode.PRODUCT_NOT_FOUND, "test", null))
            .when(productClient)
            .increaseByOrder(anyLong(), any(), anyString());

        // when
        SagaException ex = assertThrows(
            SagaException.class,
            () -> step.execute(ctx)
        );

        // then
        assertThat(ex.getType()).isEqualTo(SagaErrorType.BUSINESS);
        assertThat(ex.getMessage()).contains("Cancel INC business failure");
    }

    @Test
    void 상품서버_5xx_SagaException_RETRYABLE_던진다() {
        // given
        OrderSagaContext ctx = basicCtx();

        doThrow(new DependencyFailedException(ErrorCode.DEPENDENCY_FAILED, "test", null, null))
            .when(productClient)
            .increaseByOrder(anyLong(), any(), anyString());

        // when
        SagaException ex = assertThrows(
            SagaException.class,
            () -> step.execute(ctx)
        );

        // then
        assertThat(ex.getType()).isEqualTo(SagaErrorType.RETRYABLE);
        assertThat(ex.getMessage()).contains("Cancel INC dependency failure");
    }

    @Test
    void compensate_DEC호출_재고_되돌린다() {
        // given
        OrderSagaContext ctx = basicCtx();

        // when
        step.compensate(ctx);

        // then
        ArgumentCaptor<StockAdjustByOrderRequest> captor = ArgumentCaptor.forClass(StockAdjustByOrderRequest.class);
        verify(productClient).decreaseByOrder(eq(10L), captor.capture(), eq("DEC-1"));

        StockAdjustByOrderRequest req = captor.getValue();
        assertThat(req.getOrderId()).isEqualTo(1L);
        assertThat(req.getQuantity()).isEqualTo(10);
    }
}