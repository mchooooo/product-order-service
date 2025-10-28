package hello.orders_service.order;

import hello.orders_service.order.domain.Order;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderDataInit {
    private final EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        Order order1 = Order.create(1L, "testA", 1);
        Order order2 = Order.create(1L, "testB", 2);
        em.persist(order1);
        em.persist(order2);
    }
}
