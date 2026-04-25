package hello.orders_service.order.service;

import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxService {

    private final OrderOutboxRepository orderOutboxRepository;
    @Transactional
    public void markSent(Long id) {
        OrderOutbox outbox = orderOutboxRepository.findById(id).orElseThrow();
        outbox.markSent();
    }

    @Transactional
    public void markFailed(Long id, String errorMessage, LocalDateTime nextAttemptAt) {
        OrderOutbox outbox = orderOutboxRepository.findById(id).orElseThrow();
        outbox.markFailed(errorMessage, nextAttemptAt);
    }

    @Transactional
    public void markDead(Long id, String errorMessage) {
        OrderOutbox outbox = orderOutboxRepository.findById(id).orElseThrow();
        outbox.markDead(errorMessage);
    }

    @Transactional
    public void requeue(Long id) {
        OrderOutbox outbox = orderOutboxRepository.findById(id).orElseThrow();
        outbox.requeue();
    }
}
