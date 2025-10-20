package hello.product_service.exception;

import java.util.Map;

public class InsufficientStockException extends ApiException {
    public InsufficientStockException(Long productId, int requested, int remaining){
        super(ErrorCode.INSUFFICIENT_STOCK, "재고가 부족합니다.",
            Map.of("productId", productId, "requested", requested, "remaining", remaining));
    }
}
