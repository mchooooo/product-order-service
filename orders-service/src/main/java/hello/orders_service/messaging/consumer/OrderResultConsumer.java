package hello.orders_service.messaging.consumer;

import hello.orders_service.messaging.config.OrderRabbitConfig;
import hello.orders_service.messaging.event.StockResultEvent;
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
        // 주문 가져오기
        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();

        // 주문 상태가 PENDING이 아니라면 이미 처리가 완료된 메시지임
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("이미 처리가 완료된 주문입니다. (현재 상태: {}).", order.getStatus());
            return;
        }

        // 컨텍스트 생성, 실패 시 보상메서드에 컨텍스트를 보내야 함
        OrderSagaContext context = OrderSagaContext.from(order);

        if (event.isSuccess()) {
            // [성공] 최종 확정
            order.confirmStatus();
        } else {
            // [실패] 보상 로직 가동 (PENDING 상태인 주문을 CANCELLED로 변경 등)
            log.warn("상품 서버 처리 실패로 인한 보상 실행");
            createOrderPendingStep.compensate(context);
        }

    }
}
