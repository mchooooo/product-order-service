package hello.orders_service.order.client.exception.decoder;

import feign.Request;
import feign.Response;
import hello.orders_service.order.exception.ApiException;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ProductErrorDecoderTest {
    ProductErrorDecoder decoder = new ProductErrorDecoder();

    @Test
    void insufficientStock_400_maps_to_ApiException() {
        //given
        String body = """
          {"error":{"code":"INSUFFICIENT_STOCK","message":"재고가 부족합니다.","details":{"productId":1,"requested":3}}}
        """;
        Response resp = response(400, body);

        //when
        Exception ex = decoder.decode("methodKey", resp);

        //then
        assertThat(ex).isInstanceOf(ApiException.class);
        ApiException ae = (ApiException) ex;
        assertThat(ae.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(ae.getMessage()).isEqualTo("재고가 부족합니다.");
    }


    @Test
    void productNotFound_404_maps_to_ApiException() {
        String body = """
          {"error":{"code":"PRODUCT_NOT_FOUND","message":"상품을 찾을 수 없습니다.","details":{"productId":99}}}
        """;
        Response resp = response(404, body);

        Exception ex = decoder.decode("methodKey", resp);

        assertThat(ex).isInstanceOf(ApiException.class);
        assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void serverError_500_maps_to_DependencyFailed() {
        String body = """
          {"error":{"code":"INTERNAL_ERROR","message":"NPE","details":null}}
        """;
        Response resp = response(500, body);

        Exception ex = decoder.decode("methodKey", resp);

        assertThat(ex).isInstanceOf(DependencyFailedException.class);
        DependencyFailedException de = (DependencyFailedException) ex;
        assertThat(de.getCode()).isEqualTo(ErrorCode.DEPENDENCY_FAILED);
    }

    // response 생성 메서드
    private Response response(int status, String body) {
        Request req = Request.create(
            Request.HttpMethod.PATCH,
            "http://example.test/products/1/stock/decrease-by-order",
            Collections.emptyMap(),
            null,
            StandardCharsets.UTF_8
        );

        return Response.builder()
            .status(status)
            .reason("reason")
            .request(req)
            .headers(Collections.emptyMap())
            .body(body, StandardCharsets.UTF_8)
            .build();
    }

}