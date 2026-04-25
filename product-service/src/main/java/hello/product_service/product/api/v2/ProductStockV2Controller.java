package hello.product_service.product.api.v2;

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
@RequestMapping("/v2/products")
public class ProductStockV2Controller {
    private final InventoryService inventoryService;

    @PatchMapping("/{productId}/stock/decrease-by-order")
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrder(
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

