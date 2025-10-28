package hello.orders_service.order.repository;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByBuyerIdAndStatus(String buyerId, OrderStatus status, Pageable pageable);
    Page<Order> findByBuyerId(String buyerId, Pageable pageable);
}
