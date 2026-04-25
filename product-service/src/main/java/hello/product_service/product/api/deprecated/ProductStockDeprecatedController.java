package hello.product_service.product.api.deprecated;

import hello.product_service.common.web.response.ApiSuccess;
import hello.product_service.product.model.OrderRequest;
import hello.product_service.product.model.StockResult;
import hello.product_service.product.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/products")
public class ProductStockDeprecatedController {
    private final InventoryService inventoryService;

    @PatchMapping("/{productId}/stock/v1/decrease-by-order")
    @Deprecated
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrderV1(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        StockResult stockResult = inventoryService.decreaseByOrder(
            productId,
            orderRequest.getOrderId(),
            orderRequest.getQuantity(),
            idempotencyKey
        );

        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));
    }

    @PatchMapping("/{productId}/stock/v2/decrease-by-order")
    @Deprecated
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrderV2(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        StockResult stockResult = inventoryService.decreaseByOrderV2(
            productId,
            orderRequest.getOrderId(),
            orderRequest.getQuantity(),
            idempotencyKey
        );

        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));
    }
}

