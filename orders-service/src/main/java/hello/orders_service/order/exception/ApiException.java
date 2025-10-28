package hello.orders_service.order.exception;

public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final Object details;

    public ApiException(ErrorCode code, String msg) {
        this(code, msg, null, null);
    }

    public ApiException(ErrorCode code, String msg, Object details) {
        this(code, msg, details, null);
    }

    public ApiException(ErrorCode code, String msg, Object details, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.details = details;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
