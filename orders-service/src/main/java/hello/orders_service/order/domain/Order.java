package hello.orders_service.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
public class Order {
    @Id
    @GeneratedValue
    private Long id;

    private Long productId;

    private String  buyerId;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String failReason;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updateAt;

    //== 생성 메서드 ==//
    public static Order create(Long productId, String buyerId, int quantity) {
        Order order = new Order();
        order.buyerId = buyerId;
        order.productId = productId;
        order.quantity = quantity;
        order.status = OrderStatus.PENDING;
        return order;
    }


    // == 비즈니스 메서드 ==//
    public void createStatus() {
        this.status = OrderStatus.PENDING;
    }

    public void cancelStatus() {
        this.status = OrderStatus.CANCEL;
    }

    public void confirmStatus() {
        this.status = OrderStatus.CONFIRMED;
    }

    public void failStatus(String failReason) {
        this.status = OrderStatus.FAILED;
        this.failReason = failReason;
    }


}
