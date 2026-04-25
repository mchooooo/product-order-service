package hello.product_service.product.api.v3;

import hello.product_service.common.web.response.ApiSuccess;
import hello.product_service.product.domain.Direction;
import hello.product_service.product.model.OrderRequest;
import hello.product_service.product.model.StockResult;
import hello.product_service.product.service.InventoryService;
import hello.product_service.product.service.InventoryServiceV2;
import hello.product_service.product.service.StockLedgerService;
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
@RequestMapping("/v3/products")
public class ProductStockV3Controller {
    private final InventoryService inventoryService;
    private final StockLedgerService stockLedgerService;
    private final InventoryServiceV2 inventoryServiceV2;

    @PatchMapping("/{productId}/stock/decrease-by-order")
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrder(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        StockResult stockResult = inventoryServiceV2.decreaseByOrder(
            productId,
            orderRequest.getOrderId(),
            orderRequest.getQuantity(),
            idempotencyKey
        );

        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));
    }

    @PatchMapping("/{productId}/stock/increase-by-order")
    public ResponseEntity<ApiSuccess<StockResult>> increaseByOrder(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        StockResult stockResult = inventoryService.increaseByOrder(
            productId,
            orderRequest.getOrderId(),
            orderRequest.getQuantity(),
            idempotencyKey
        );

        stockLedgerService.save(
            productId,
            Direction.IN,
            orderRequest.getQuantity(),
            orderRequest.getOrderId(),
            idempotencyKey
        );

        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));
    }
}

