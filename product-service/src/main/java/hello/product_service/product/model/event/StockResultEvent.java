package hello.product_service.product.model.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockResultEvent {
    private Long orderId;
    private boolean success;
    private String reason;
}
