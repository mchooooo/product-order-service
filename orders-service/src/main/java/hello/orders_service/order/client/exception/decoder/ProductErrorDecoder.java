package hello.orders_service.order.client.exception.decoder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import hello.orders_service.common.ApiError;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.ErrorCode;
import hello.orders_service.order.exception.client.InsufficientStockException;
import hello.orders_service.order.exception.client.ProductNotFoundException;

import java.io.InputStream;

public class ProductErrorDecoder implements ErrorDecoder {

    private final ObjectMapper om = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        ApiError apiError = readBody(response);
        String code = apiError == null ? null : apiError.getCode();
        String message = apiError == null ? null : apiError.getMessage();
        int status = response.status();
        Object details = apiError == null ? null : apiError.getDetails();


        // 400 INSUFFICIENT STOCK 예외 변환
        if ("INSUFFICIENT_STOCK".equals(code) && status == 400) {
            return new InsufficientStockException(ErrorCode.INSUFFICIENT_STOCK, message, details, null);
        }

        // 404 PRODUCT NOT FOUND 예외 변환
        if ("PRODUCT_NOT_FOUND".equals(code) && status == 404) {
            return new ProductNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, message, details, null);
        }

        // 500 호출 서버 실패 (재시도 대상)
        return new DependencyFailedException(
            ErrorCode.DEPENDENCY_FAILED,
            "Product service call failed",
            details,
            null
        );
    }

    private ApiError readBody(Response response) {
        if (response.body() != null) {
            try (InputStream inp = response.body().asInputStream()) {
                byte[] bytes = inp.readAllBytes();
                // 비어있지 않으면 매핑 시도
                if (bytes.length == 0) {
                    return null;
                }

                // error 하위 부터 읽음
                JsonNode root = om.readTree(bytes).path("error");
                return om.treeToValue(root, ApiError.class);

            } catch (Exception e) {
                e.printStackTrace();
                // body 파싱 실패 시 null 반환
            }
        }
        return null;
    }

}
