package hello.product_service.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final Object object;
    public ApiException(ErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
        this.object = null;
    }

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.object = null;
    }

    public ApiException(ErrorCode code, String message, Object o) {
        super(message);
        this.code = code;
        this.object = o;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.object = null;
    }
}
