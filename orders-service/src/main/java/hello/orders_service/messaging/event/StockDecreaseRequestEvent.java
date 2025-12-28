package hello.orders_service.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDecreaseRequestEvent {
    private Long orderId;
    private Long productId;
    private Integer qty;
    private String requestId; // 멱등성 보장을 위한 키
}
