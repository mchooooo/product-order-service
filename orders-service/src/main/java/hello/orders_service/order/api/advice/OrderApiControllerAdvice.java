package hello.orders_service.order.api.advice;

import hello.orders_service.common.ApiError;
import hello.orders_service.order.exception.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderApiControllerAdvice {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException e) {

        ApiError err = new ApiError(e.getCode().name(),
            e.getMessage() != null ? e.getMessage() : e.getCode().getMessage(),
            e.getCode().getStatus(),
            e.getDetails());

        return ResponseEntity.status(e.getCode().getStatus()).body(err);
    }
}
