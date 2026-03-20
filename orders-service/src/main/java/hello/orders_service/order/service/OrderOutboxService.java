package hello.orders_service.order.service;

import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.repository.OrderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void markFailed(Long id) {
        OrderOutbox outbox = orderOutboxRepository.findById(id).orElseThrow();
        outbox.markFailed();
    }
}
