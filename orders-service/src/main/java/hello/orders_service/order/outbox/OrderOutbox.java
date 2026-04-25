package hello.orders_service.order.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "order_outbox")
@NoArgsConstructor
public class OrderOutbox {
    @Id
    @GeneratedValue
    private Long id;
    
    // 어떤 주문에 대한 이벤트인지
    private Long orderId;

    // 어떤 이벤트인지 (STOCK_DECREASE_REQUEST, STOCK_DECREASE_RESULT, STOCK_INCREASE_REQUEST, STOCK_INCREASE_RESULT)
    @Column(nullable = false)
    private String eventType;

    // RabbitMQ 로 보낼 실제 메시지 내용(JSON 형식)
    @Lob
    @Column(nullable = false)
    private String payload;

     // PENDING, SENT, FAILED 등
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    // 재시도 횟수
    @Column(nullable = false)
    private int retryCount = 0;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime lastAttemptAt;

    private LocalDateTime sentAt;

    @Column(length = 1000)
    private String lastError;

    //== 생성 메서드 ==//
    public static OrderOutbox pending(Long orderId, String eventType, String payload) {
        OrderOutbox outbox = new OrderOutbox();
        outbox.orderId = orderId;
        outbox.eventType = eventType;
        outbox.payload = payload;
        outbox.status = OutboxStatus.PENDING;
        outbox.nextAttemptAt = LocalDateTime.now();
        return outbox;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.lastAttemptAt = this.sentAt;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markFailed(String errorMessage, LocalDateTime nextAttemptAt) {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
        this.lastAttemptAt = LocalDateTime.now();
        this.lastError = errorMessage;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markDead(String errorMessage) {
        this.status = OutboxStatus.DEAD;
        this.lastAttemptAt = LocalDateTime.now();
        this.lastError = errorMessage;
        this.nextAttemptAt = null;
    }

    public void requeue() {
        this.status = OutboxStatus.PENDING;
        this.lastError = null;
        this.nextAttemptAt = LocalDateTime.now();
    }
}
