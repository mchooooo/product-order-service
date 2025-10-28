package hello.orders_service.order.exception;

public class DependencyFailedException extends ApiException {
    public DependencyFailedException(ErrorCode code, String msg, Object details, Throwable cause) {
        super(code, msg, details, cause);
    }
}
