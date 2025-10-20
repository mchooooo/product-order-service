package hello.product_service.exception;

import java.util.Map;

public class IdempotencyMismatchException extends ApiException  {
    public IdempotencyMismatchException(String key){
        super(ErrorCode.IDEMPOTENCY_MISMATCH, "멱등 요청 키 값이 다릅니다.", Map.of("idempotencyKey", key));
    }
}
