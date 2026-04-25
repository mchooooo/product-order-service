package hello.orders_service.common.web.advice;

import hello.orders_service.common.web.response.ApiError;
import hello.orders_service.order.exception.ApiException;
import hello.orders_service.order.exception.client.ProductClientException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderApiAdvice {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException e) {
        ApiError err = new ApiError(
            e.getCode().name(),
            e.getMessage() != null ? e.getMessage() : e.getCode().getMessage(),
            e.getCode().getStatus(),
            e.getDetails()
        );

        return ResponseEntity.status(e.getCode().getStatus()).body(err);
    }

    @ExceptionHandler(ProductClientException.class)
    public ResponseEntity<ApiError> handleProductClient(ProductClientException e) {
        ApiError err = new ApiError(
            e.getCode() != null ? e.getCode().name() : "BUSINESS_ERROR",
            e.getMessage() != null ? e.getMessage() : "Downstream business error",
            e.getCode().getStatus(),
            e.getDetails()
        );

        return ResponseEntity.status(err.getStatus()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception e) {
        ApiError err = new ApiError("INTERNAL_ERROR", "서버 오류가 발생했습니다.", 500, null);
        return ResponseEntity.status(500).body(err);
    }
}

