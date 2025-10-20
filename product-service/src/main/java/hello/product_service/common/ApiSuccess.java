package hello.product_service.common;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiSuccess <T> {
    private T data;
    private Object details;

    public ApiSuccess() {
    }

    public ApiSuccess(T data, Object details) {
        this.data = data;
        this.details = details;
    }

    public static <T> ApiSuccess<T> of(T data, Object details) {
        return new ApiSuccess<>(data, details);
    }
}
