package hello.product_service.product.api.advice;

import hello.product_service.common.ApiError;
import hello.product_service.common.ApiErrorResponse;
import hello.product_service.exception.ApiException;
import hello.product_service.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class ProductApiAdvice {

    private ResponseEntity<ApiErrorResponse> toResponse(ErrorCode code, String message, Object details) {
        ApiError err = new ApiError(code.name(), message != null ? message : code.defaultMessage(), code.status(), details);
        return ResponseEntity.status(code.status()).body(ApiErrorResponse.of(err));
    }

    /* ===== 도메인/비즈니스 예외 ===== */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException e) {
        return toResponse(e.getCode(), e.getMessage(), e.getObject());
    }


    /* ===== 검증/바인딩/파싱 오류 → 400 ===== */
    @ExceptionHandler({ BindException.class, ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception e) {
        return toResponse(ErrorCode.BAD_REQUEST, e.getMessage(), null);
    }

    /* ===== 기타 → 500 ===== */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleFallback(Exception e) {
        // 5xx는 서버 로그에 stacktrace 남기기
         log.error("Unhandled", e);
        return toResponse(ErrorCode.INTERNAL_ERROR, null, null);
    }

}
