package hello.product_service.product.api.deprecated;

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

/**
 * 레거시(비표준) 경로 유지용 컨트롤러.
 * 신규 클라이언트는 {@code /v1|/v2|/v3} 프리픽스 경로를 사용하세요.
 */
@Deprecated
@RequiredArgsConstructor
@RestController
@RequestMapping("/products")
public class ProductStockLegacyPathController {
    private final InventoryService inventoryService;
    private final StockLedgerService stockLedgerService;
    private final InventoryServiceV2 inventoryServiceV2;

    @PatchMapping("/{productId}/stock/v3/decrease-by-order")
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrderV3LegacyPath(
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
    public ResponseEntity<ApiSuccess<StockResult>> increaseByOrderLegacyPath(
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

