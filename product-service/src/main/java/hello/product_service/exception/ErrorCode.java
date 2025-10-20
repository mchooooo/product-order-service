package hello.product_service.exception;

public enum ErrorCode {
    PRODUCT_NOT_FOUND(404, "상품을 찾을 수 없습니다."),
    VALIDATION_ERROR(400, "요청 형식이 올바르지 않습니다."),
    BAD_REQUEST(400, "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 메서드입니다."),
    INSUFFICIENT_STOCK(400, "재고가 부족합니다."),
    IDEMPOTENCY_MISMATCH(409, "멱등 요청 키 값이 다릅니다."),
    DUPLICATE_RESOURCE(409, "리소스가 중복됩니다."),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다.");

    private final int status; private final String defaultMessage;
    ErrorCode(int status, String defaultMessage){ this.status=status; this.defaultMessage=defaultMessage; }
    public int status(){ return status; }
    public String defaultMessage(){ return defaultMessage; }
}
