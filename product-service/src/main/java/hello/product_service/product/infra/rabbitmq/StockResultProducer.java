package hello.product_service.product.infra.rabbitmq;

import hello.product_service.product.model.event.StockResultEvent;
import hello.product_service.product.util.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockResultProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendResult(StockResultEvent event) {
        log.info("재고 감소 결과 메시지 전송 - 주문ID: {}", event.getOrderId());
        rabbitTemplate.convertAndSend(
            RabbitMqConfig.ORDER_RESULT_EXCHANGE,
            RabbitMqConfig.ORDER_RESULT_ROUTING_KEY,
            event
        );

    }
}
