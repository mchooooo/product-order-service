package hello.orders_service.order.exception;

public class OrderStateException extends ApiException {
    public OrderStateException(String msg, Object details) {
        super(ErrorCode.INVALID_STATE, msg, details);
    }
}
