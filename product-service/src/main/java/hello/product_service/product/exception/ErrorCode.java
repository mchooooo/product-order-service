package hello.product_service.product.exception;

public enum ErrorCode {
    PRODUCT_NOT_FOUND(404, "상품을 찾을 수 없습니다."),
    VALIDATION_ERROR(400, "요청 형식이 올바르지 않습니다."),
    BAD_REQUEST(400, "잘못된 요청입니다."),
    INSUFFICIENT_STOCK(400, "재고가 부족합니다."),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다."),
    RETRY_LATER(503, "처리 중 충돌이 발생했습니다, 잠시 후 다시 시도해주세요");

    private final int status; private final String defaultMessage;
    ErrorCode(int status, String defaultMessage){ this.status=status; this.defaultMessage=defaultMessage; }
    public int status(){ return status; }
    public String defaultMessage(){ return defaultMessage; }
}
