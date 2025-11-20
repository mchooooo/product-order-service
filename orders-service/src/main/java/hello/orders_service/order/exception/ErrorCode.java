package hello.orders_service.order.exception;

public enum ErrorCode {
    ORDER_NOT_FOUND(404, "주문을 찾을 수 없습니다."),
    INVALID_STATE(409, "상태 전이 불가"),
    DEPENDENCY_FAILED(424, "의존 서비스 실패"),
    VALIDATION_ERROR(400, "요청 형식이 올바르지 않습니다."),
    INSUFFICIENT_STOCK(400, "재고가 부족합니다."),
    PRODUCT_NOT_FOUND(404, "상품을 찾을 수 없습니다.");

    private final int status; private final String message;
    ErrorCode(int s, String m){ this.status=s; this.message=m; }
    public int getStatus(){ return status; }
    public String getMessage(){ return message; }
}
