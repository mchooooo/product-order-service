package hello.orders_service.order.infra.messaging.consumer;

import hello.orders_service.order.infra.config.OrderRabbitConfig;
import hello.orders_service.order.infra.messaging.event.StockResultEvent;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.repository.OrderRepository;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.order.saga.step.CreateOrderPendingStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderResultConsumer {
    private final OrderRepository orderRepository;
    private final CreateOrderPendingStep createOrderPendingStep;

    @RabbitListener(queues = OrderRabbitConfig.ORDER_RESULT_QUEUE)
    @Transactional
    public void handleOrderResult(StockResultEvent event) {
        log.info("메시지 수신 - 주문 ID: {}, 결과: {}", event.getOrderId(), event.isSuccess());
        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("이미 처리가 완료된 주문입니다. (현재 상태: {}).", order.getStatus());
            return;
        }

        OrderSagaContext context = OrderSagaContext.from(order);

        if (event.isSuccess()) {
            order.confirmStatus();
        } else {
            log.warn("상품 서버 처리 실패로 인한 보상 실행");
            createOrderPendingStep.compensate(context);
        }
    }
}

