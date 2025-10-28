package hello.orders_service.order.client.dto;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class StockResult {
    private boolean success;
    private Integer remainingStock;
    private String message;
}
