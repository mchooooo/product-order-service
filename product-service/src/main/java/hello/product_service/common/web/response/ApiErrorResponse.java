package hello.product_service.common.web.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ApiErrorResponse {
    private ApiError error;

    public static ApiErrorResponse of(ApiError error) {
        return new ApiErrorResponse(error);
    }
}

