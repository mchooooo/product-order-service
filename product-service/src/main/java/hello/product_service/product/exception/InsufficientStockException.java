package hello.product_service.product.exception;

import java.util.Map;

public class InsufficientStockException extends ApiException {
    public InsufficientStockException(Long productId, int remaining){
        super(ErrorCode.INSUFFICIENT_STOCK, "재고가 부족합니다.",
            Map.of("productId", productId, "remaining", remaining));
    }
}
