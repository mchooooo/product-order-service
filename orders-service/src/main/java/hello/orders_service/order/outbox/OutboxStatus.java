package hello.orders_service.order.outbox;

public enum OutboxStatus {
    PENDING, SENT, FAILED, DEAD
}
