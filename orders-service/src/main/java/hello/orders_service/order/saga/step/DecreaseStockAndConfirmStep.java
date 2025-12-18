package hello.orders_service.order.saga.step;

import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.client.dto.StockResult;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.exception.ApiException;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.client.ProductClientException;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.order.service.OrderService;
import hello.orders_service.saga.SagaException;
import hello.orders_service.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DecreaseStockAndConfirmStep implements SagaStep<OrderSagaContext> {
    private final ProductClient productClient;
    private final OrderService orderService;

    @Override
    public void execute(OrderSagaContext context) {
        Long orderId = context.getOrderId();
        Long productId = context.getProductId();
        int qty = context.getQuantity();
        String decKey = context.getDecKey();

        StockAdjustByOrderRequest request = new StockAdjustByOrderRequest(orderId, qty);

        try {
            // 상품서버 호출
            StockResult stockResult = productClient.decreaseByOrder(productId, request, decKey).getData();

            log.info("Stock decreased. orderId={}, productId={}, qty={}",
                orderId, productId, qty);

            // 기존 테스트용 로컬 예외
            if ("apiEx".equals(context.getBuyerId())) {
                throw new ApiException(null, "test call");
            }

            Order confirmed = orderService.confirmOrder(orderId);
            context.setOrder(confirmed);
            log.info("Order CONFIRMED. orderId={}", orderId);

        } catch (ProductClientException ex) {
            // --- 4xx: 재고 차감 실패 → 보상 불필요, 주문 FAILED 기록 ---
            log.warn("error from product. orderId={}, msg={}", orderId, ex.getMessage());

            Order failed = orderService.failOrder(orderId, ex.getMessage());
            context.setOrder(failed);

            throw SagaException.business("Order failed by product client", ex);

        } catch (DependencyFailedException ex) {
            // --- 5xx/타임아웃: 재시도 대상 ---
            log.error("Dependency failure (retry target). orderId={}", orderId, ex);
            throw SagaException.retryable("Upstream dependency failure", ex);

        } catch (ApiException ex) {
            // --- 로컬 예외: 재고 감소는 성공했을 수 있음, 보상 대상 ---
            log.error("Local ApiException after DEC. orderId={}", orderId, ex);
            throw SagaException.compensate("Local ApiException after DEC", ex);

        } catch (RuntimeException ex) {
            log.error("Unknown local error after stock decrease. orderId={}", orderId, ex);
            throw SagaException.compensate("Unknown local error after stock decrease", ex);
        }

    }

    @Override
    public void compensate(OrderSagaContext context) {
        // 주문 생성 사가에서 보상: 재고 다시 증가 + 주문 FAILED(COMPENSATED)
        Long orderId = context.getOrderId();
        Long productId = context.getProductId();
        int qty = context.getQuantity();

        try {
            String incKey = context.getIncKey(); // INC-{orderId}
            StockAdjustByOrderRequest incReq = new StockAdjustByOrderRequest(orderId, qty);

            log.info("Compensate stock. increase stock. orderId={}, productId={}, qty={}",
                orderId, productId, qty);

            productClient.increaseByOrder(productId, incReq, incKey);
            Order failed = orderService.failOrder(orderId, "COMPENSATED");
            context.setOrder(failed);

        } catch (Exception ex) {
            // 보상 실패 시
            log.error("Compensation INC failed. orderId={}", orderId, ex);
        }

    }
}
