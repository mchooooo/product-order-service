package hello.orders_service.order.saga.step;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.exception.ErrorCode;
import hello.orders_service.order.exception.OrderNotFoundException;
import hello.orders_service.order.repository.OrderRepository;
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
    private final OrderRepository orderRepository;

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
        log.warn("보상 트랜잭션 실행: 주문 ID = {}", context.getOrderId());

        // DB에서 해당 주문을 찾음
        Order order = orderRepository.findById(context.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException(ErrorCode.ORDER_NOT_FOUND, ErrorCode.ORDER_NOT_FOUND.getMessage(), null, null));

        // 주문 상태를 실패/취소로 변경
        // 이미 PENDING으로 저장된 데이터가 DB에 있으므로, 이를 무효화하는 작업이 필요함
        order.failStatus("PRODUCT SERVER ERROR");

        orderRepository.save(order);

        log.info("주문 ID {} 상태가 FAILED로 변경되었습니다.", context.getOrderId());
    }
}
