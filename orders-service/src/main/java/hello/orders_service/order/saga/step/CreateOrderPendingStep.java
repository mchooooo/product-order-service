package hello.orders_service.order.saga.step;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.order.service.OrderService;
import hello.orders_service.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreateOrderPendingStep implements SagaStep<OrderSagaContext> {
    private final OrderService orderService;
    @Override
    public void execute(OrderSagaContext context) {
        Order order = orderService.createOrderPending(
            context.getProductId(),
            context.getBuyerId(),
            context.getQuantity()
        );
        context.setOrder(order);
        context.setOrderId(order.getId());
        context.setDecKey("DEC-" + order.getId());
        context.setIncKey("INC-" + order.getId());

        log.info("Order PENDING created. orderId={}", order.getId());
    }

    @Override
    public void compensate(OrderSagaContext context) {
        // NO-OP
        log.debug("CreateOrderPendingStep compensate NO-OP. orderId={}", context.getOrderId());
    }
}
