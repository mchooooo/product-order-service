package hello.orders_service.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StockAdjustByOrderRequest {
    private Long orderId;
    private Integer quantity;

    public static StockAdjustByOrderRequest create(Long orderId, Integer quantity) {
        return new StockAdjustByOrderRequest(orderId, quantity);
    }

}
