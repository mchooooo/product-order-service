package hello.orders_service.order.client;

import hello.orders_service.common.ApiSuccess;
import hello.orders_service.order.client.dto.ProductDto;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.client.dto.StockResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "productClient",
    url = "${product.base-url}"
)
public interface ProductClient {
    @PatchMapping("/products/{id}/stock/v3/decrease-by-order")
    ApiSuccess<StockResult> decreaseByOrder(@PathVariable("id") Long productId,
                               @RequestBody StockAdjustByOrderRequest body,
                               @RequestHeader("Idempotency-Key") String idemKey);

    @PatchMapping("/products/{id}/stock/increase-by-order")
    ApiSuccess<StockResult> increaseByOrder(@PathVariable("id") Long productId,
                                @RequestBody StockAdjustByOrderRequest body,
                                @RequestHeader("Idempotency-Key") String idemKey);

    @GetMapping("/products/{id}")
    ProductDto getProduct(@PathVariable("id") Long id);
}
