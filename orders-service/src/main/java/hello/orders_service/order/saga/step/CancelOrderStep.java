package hello.orders_service.order.saga.step;

import hello.orders_service.order.domain.Order;
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
public class CancelOrderStep implements SagaStep<OrderSagaContext> {

    private final OrderService orderService;

    @Override
    public void execute(OrderSagaContext context) {
        Long orderId = context.getOrderId();


        try {
            log.info("Cancel saga - cancelling order. orderId={}", orderId);

            Order canceled = orderService.cancelOrder(orderId);
            context.setOrder(canceled);

        } catch (RuntimeException ex) {
            // ApiException, RuntimeException → 재고는 증가했고 주문은 취소 실패
            // 여기서는 모두 보상 대상으로 보고 상위에서 보상(DEC) 트리거
            log.error("Cancel local error. orderId={}", orderId, ex);
            throw SagaException.compensate("CancelOrderStep failed", ex);
        }

    }

    @Override
    public void compensate(OrderSagaContext context) {
        // 주문 상태는 기존 CONFIRMED 로 유지시키는 전략이므로 여기서는 NO-OP.
        // (재고 되돌리기는 IncreaseStockByCancelStep.compensate 에서 수행)
        log.debug("CancelOrderStep compensate NO-OP. orderId={}", context.getOrderId());
    }
}
