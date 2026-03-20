package hello.orders_service.order.repository;

import hello.orders_service.order.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import hello.orders_service.order.outbox.OrderOutbox;
import java.util.List;
import java.util.Collection;

@Repository
public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, Long> {
    List<OrderOutbox> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    // retryCount 한도 미만인 outbox만 재시도 대상으로 조회
    List<OrderOutbox> findTop100ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
        Collection<OutboxStatus> statuses,
        int retryCount
    );
}