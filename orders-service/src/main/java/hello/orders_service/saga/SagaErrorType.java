package hello.orders_service.saga;

public enum SagaErrorType {
    BUSINESS,        // 보상 X (이미 fail 처리)
    RETRYABLE,       // 재시도 대상
    COMPENSATE    // 보상 대상
}
