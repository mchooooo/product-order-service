package hello.orders_service.order.dto.response;

import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.outbox.OutboxStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class OutboxResponse {
    private Long id;
    private Long orderId;
    private String eventType;
    private OutboxStatus status;
    private int retryCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;

    public static OutboxResponse from(OrderOutbox outbox) {
        return new OutboxResponse(
            outbox.getId(),
            outbox.getOrderId(),
            outbox.getEventType(),
            outbox.getStatus(),
            outbox.getRetryCount(),
            outbox.getNextAttemptAt(),
            outbox.getLastError()
        );
    }
}
