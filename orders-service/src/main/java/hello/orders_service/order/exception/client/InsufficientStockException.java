package hello.orders_service.order.exception.client;

import hello.orders_service.order.exception.ErrorCode;

public class InsufficientStockException extends ProductClientException {
    public InsufficientStockException(ErrorCode code, String msg, Object details, Throwable cause) {
        super(code, msg, details, cause);
    }
}
