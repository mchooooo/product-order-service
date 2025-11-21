package hello.orders_service.order.exception;

public class OrderNotFoundException extends ApiException {
    public OrderNotFoundException(ErrorCode code, String msg, Object details, Throwable cause) {
        super(code, msg, details, cause);
    }
}
