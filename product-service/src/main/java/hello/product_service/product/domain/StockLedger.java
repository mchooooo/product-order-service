package hello.product_service.product.domain;

import hello.product_service.product.model.StockResult;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
public class StockLedger {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    private Reason reason;

    private int quantity;

    @Column(name = "orders_id")
    private Long orderId;

    private String requestId;

    @CreationTimestamp
    private LocalDateTime createAt;


    // == 엔티티 생성 메서드 == //
    public static StockLedger create(Product product, Direction direction, Reason reason, int quantity, Long orderId, String requestId) {
        StockLedger stockLedger = new StockLedger();
        stockLedger.product = product;
        stockLedger.direction = direction;
        stockLedger.reason = reason;
        stockLedger.quantity = quantity;
        stockLedger.orderId = orderId;
        stockLedger.requestId = requestId;

        return stockLedger;
    }


}
