package hello.product_service.product.model;

import hello.product_service.product.domain.IdempotencyRecord;
import hello.product_service.product.domain.IdempotencyStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
public class StockResult {
    private boolean success;
    private Integer remainingStock;
    private String message;

    public static StockResult create(IdempotencyRecord idempotencyRecord) {
        boolean success = idempotencyRecord.getStatus() == IdempotencyStatus.SUCCESS;
        return new StockResult(success, idempotencyRecord.getRemainingStock(), idempotencyRecord.getMessage());
    }
}
