package hello.orders_service.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.exception.OrderNotFoundException;
import hello.orders_service.order.exception.OrderStateException;
import hello.orders_service.order.exception.ErrorCode;
import hello.orders_service.order.infra.messaging.event.StockDecreaseRequestEvent;
import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.outbox.OutboxStatus;
import hello.orders_service.order.repository.OrderOutboxRepository;
import hello.orders_service.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderRecoveryService {
    private final OrderRepository orderRepository;
    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;
    private final OrderOutboxService orderOutboxService;

    @Transactional
    public Order retryFailedOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(ErrorCode.ORDER_NOT_FOUND, ErrorCode.ORDER_NOT_FOUND.getMessage(), null, null));

        if (order.getStatus() != OrderStatus.FAILED) {
            throw new OrderStateException("현재 상태(" + order.getStatus() + ")에서는 재처리할 수 없습니다.", order.getStatus().name());
        }

        order.retryStatus();

        StockDecreaseRequestEvent event = new StockDecreaseRequestEvent(
            order.getId(),
            order.getProductId(),
            order.getQuantity(),
            "DEC-" + order.getId()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            orderOutboxRepository.save(OrderOutbox.pending(order.getId(), "STOCK_DECREASE_REQUEST", payload));
            return order;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("재처리용 Outbox 직렬화 실패", e);
        }
    }

    @Transactional
    public OrderOutbox requeueOutbox(Long outboxId) {
        OrderOutbox outbox = orderOutboxRepository.findById(outboxId).orElseThrow();

        if (outbox.getStatus() != OutboxStatus.FAILED && outbox.getStatus() != OutboxStatus.DEAD) {
            throw new IllegalStateException("FAILED 또는 DEAD 상태의 Outbox만 재발행할 수 있습니다.");
        }

        orderOutboxService.requeue(outboxId);
        return orderOutboxRepository.findById(outboxId).orElseThrow();
    }
}
