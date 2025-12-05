package hello.orders_service.saga;

public class SagaException extends RuntimeException {
    private final SagaErrorType type;

    public SagaException(SagaErrorType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public SagaErrorType getType() {
        return type;
    }

    public static SagaException business(String msg, Throwable cause) {
        return new SagaException(SagaErrorType.BUSINESS, msg, cause);
    }

    public static SagaException retryable(String msg, Throwable cause) {
        return new SagaException(SagaErrorType.RETRYABLE, msg, cause);
    }

    public static SagaException compensate(String msg, Throwable cause) {
        return new SagaException(SagaErrorType.COMPENSATE, msg, cause);
    }
}
