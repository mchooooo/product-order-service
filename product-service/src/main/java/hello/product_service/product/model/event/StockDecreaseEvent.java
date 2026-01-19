package hello.product_service.product.model.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDecreaseEvent {
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private String requestId;
}
