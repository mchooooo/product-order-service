package hello.orders_service.order.saga.step;

import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.client.ProductClientException;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.saga.SagaException;
import hello.orders_service.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncreaseStockByCancelStep implements SagaStep<OrderSagaContext> {
    private final ProductClient productClient;

    @Override
    public void execute(OrderSagaContext context) {
        Long orderId = context.getOrderId();
        Long productId = context.getProductId();
        int qty = context.getQuantity();
        String incKey = context.getIncKey(); // INC-{orderId}

        StockAdjustByOrderRequest request = new StockAdjustByOrderRequest(orderId, qty);

        try {
            log.info("Cancel saga - increase stock. orderId={}, productId={}, qty={}",
                orderId, productId, qty);

            productClient.increaseByOrder(productId, request, incKey);
        } catch (DependencyFailedException ex) {
            //재시도 대상 -> 예외 그대로 던졌었음
            log.error("Cancel dependency failure (retry target). orderId={}", orderId, ex);
            throw SagaException.retryable("Cancel INC dependency failure", ex);

        } catch (ProductClientException ex) {
            // 4xx 비즈니스 실패 → 주문은 CONFIRMED 유지, 그냥 반환
            log.warn("Cancel business failure. orderId={}, msg={}", orderId, ex.getMessage());
            throw SagaException.business("Cancel INC business failure", ex);
        }

    }

    @Override
    public void compensate(OrderSagaContext context) {
        // 취소 플로우에서 보상: INC 를 되돌리기 위해 DEC 호출
        Long orderId = context.getOrderId();
        Long productId = context.getProductId();
        int qty = context.getQuantity();
        String decKey = context.getDecKey(); // DEC-{orderId}

        try {
            log.info("Cancel saga - compensating INC with DEC. orderId={}, productId={}, qty={}",
                orderId, productId, qty);

            StockAdjustByOrderRequest decReq = new StockAdjustByOrderRequest(orderId, qty);
            productClient.decreaseByOrder(productId, decReq, decKey);

            // 주문 상태는 CONFIRMED 그대로 유지

        } catch (Exception ex) {
            log.error("Cancel compensation DEC failed. orderId={}", orderId, ex);
        }
    }
}
