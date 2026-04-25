package hello.orders_service.order.infra.messaging.publisher;

import hello.orders_service.order.infra.config.OrderRabbitConfig;
import hello.orders_service.order.service.OrderService;
import hello.orders_service.order.service.OrderOutboxService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import hello.orders_service.order.repository.OrderOutboxRepository;
import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.outbox.OutboxStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    private final OrderService orderService;

    private static final int MAX_RETRY_COUNT = 5;
    private static final int RETRY_DELAY_SECONDS = 30;

    // 5초마다 실행
    @Scheduled(fixedDelay = 5000)
    public void publishDueOutboxEvents() {
        List<OrderOutbox> outboxes = orderOutboxRepository.findTop100ByStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
            MAX_RETRY_COUNT,
            LocalDateTime.now()
        );

        if (outboxes.isEmpty()) {
            return;
        }

        log.info("Outbox 메시지 발행 시작 [개수: {}]", outboxes.size());

        for (OrderOutbox outbox : outboxes) {
            try {
                if ("STOCK_DECREASE_REQUEST".equals(outbox.getEventType())) {
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

                int nextRetryCount = outbox.getRetryCount() + 1;
                String errorMessage = trimErrorMessage(e);

                if (nextRetryCount >= MAX_RETRY_COUNT) {
                    orderOutboxService.markDead(outbox.getId(), errorMessage);
                    orderService.failOrderIfPending(outbox.getOrderId(), "OUTBOX_RETRY_EXHAUSTED");
                    log.error("Outbox retry exhausted. orderId={}, outboxId={}, nextRetryCount={}",
                        outbox.getOrderId(), outbox.getId(), nextRetryCount);
                } else {
                    LocalDateTime nextAttemptAt = LocalDateTime.now().plusSeconds((long) RETRY_DELAY_SECONDS * nextRetryCount);
                    orderOutboxService.markFailed(outbox.getId(), errorMessage, nextAttemptAt);
                }
            }
        }
    }

    private String trimErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
