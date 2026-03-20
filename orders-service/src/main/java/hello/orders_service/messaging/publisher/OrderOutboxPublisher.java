package hello.orders_service.messaging.publisher;

import hello.orders_service.messaging.config.OrderRabbitConfig;
import hello.orders_service.order.service.OrderOutboxService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import hello.orders_service.order.repository.OrderOutboxRepository;
import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.outbox.OutboxStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;


@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxPublisher {
    private final OrderOutboxRepository orderOutboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OrderOutboxService orderOutboxService;

    private static final int MAX_RETRY_COUNT = 5;

    // 5초마다 실행
    @Scheduled(fixedDelay = 5000)
    public void publishDueOutboxEvents() {
        List<OrderOutbox> outboxes = orderOutboxRepository.findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
            MAX_RETRY_COUNT
        );

        if (outboxes.isEmpty()) {
            return;
        }

        log.info("Outbox 메시지 발행 시작 [개수: {}]", outboxes.size());

        for (OrderOutbox outbox : outboxes) {
            try {
                // eventType 에 따라 라우팅 키/익스체인지 분기 가능
                if ("STOCK_DECREASE_REQUEST".equals(outbox.getEventType())) {
                    // payload은 이미 JSON 문자열이므로 convertAndSend(String)로 이중 인코딩되지 않게
                    // Message 바디로 "그대로" 실어 보낸다.
                    var message = MessageBuilder
                        .withBody(outbox.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .build();

                    rabbitTemplate.send(
                        OrderRabbitConfig.STOCK_REQUEST_EXCHANGE,
                        OrderRabbitConfig.STOCK_REQUEST_ROUTING_KEY,
                        message
                    );
                } else {
                    throw new IllegalArgumentException("Unsupported outbox eventType: " + outbox.getEventType());
                }

                orderOutboxService.markSent(outbox.getId());
                log.info("Outbox 발행 성공. outboxId={}, orderId={}", outbox.getId(), outbox.getOrderId());

            } catch (Exception e) {
                log.error("Outbox 발행 실패. outboxId={}, orderId={}", outbox.getId(), outbox.getOrderId(), e);
                orderOutboxService.markFailed(outbox.getId());
            }
        }
    }

}