package hello.orders_service.order.saga.step;

import hello.orders_service.messaging.config.OrderRabbitConfig;
import hello.orders_service.messaging.event.StockDecreaseRequestEvent;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitMqStockDecreaseStep implements SagaStep<OrderSagaContext> {
    private final RabbitTemplate rabbitTemplate;
    @Override
    public void execute(OrderSagaContext context) {
        log.info("주문 서버 -> MQ: 재고 감소 요청 발행 [주문ID: {}]", context.getOrderId());
        // 이벤트 생성
        StockDecreaseRequestEvent event = new StockDecreaseRequestEvent(
            context.getOrderId(), context.getProductId(), context.getQuantity(), context.getDecKey());

        // RabbitMQ로 메시지 전송
        // 응답을 기다리지 않고 다음 로직으로 이동함 (비동기)
        rabbitTemplate.convertAndSend(
            OrderRabbitConfig.STOCK_REQUEST_EXCHANGE,
            OrderRabbitConfig.STOCK_REQUEST_ROUTING_KEY,
            event
        );

        log.info("재고 감소 메시지 발행 완료");
    }

    @Override
    public void compensate(OrderSagaContext context) {
        log.error("재고 감소 단계 실패 롤백 처리 [주문ID: {}]", context.getOrderId());

    }
}
