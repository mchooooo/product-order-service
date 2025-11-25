package hello.orders_service.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class StockResult {
    private boolean success;
    private Integer remainingStock;
    private String message;
}
