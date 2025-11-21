package hello.orders_service.order.exception.client;

import hello.orders_service.order.exception.ErrorCode;
import lombok.Getter;

@Getter
public class ProductClientException extends RuntimeException {
    private final ErrorCode code;
    private final Object details;

    public ProductClientException (ErrorCode code, String msg, Object details) {
        this(code, msg, details, null);
    }

    public ProductClientException (ErrorCode code, String msg, Object details, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.details = details;
    }
}
