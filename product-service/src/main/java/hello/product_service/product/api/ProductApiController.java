package hello.product_service.product.api;

import hello.product_service.common.ApiSuccess;
import hello.product_service.product.domain.Direction;
import hello.product_service.product.model.*;
import hello.product_service.product.service.InventoryService;
import hello.product_service.product.service.InventoryServiceV2;
import hello.product_service.product.service.ProductService;
import hello.product_service.product.service.StockLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/products")
public class ProductApiController {
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final StockLedgerService stockLedgerService;
    private final InventoryServiceV2 inventoryServiceV2;
    @PostMapping
    public ResponseEntity<ApiSuccess<ProductDto>> create(@RequestBody ProductCreateRequest productDto) {
        Long createdId = productService.create(productDto);
        ProductDto createdProduct = productService.findById(createdId);

        return ResponseEntity.ok(ApiSuccess.of(createdProduct, null));
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<ApiSuccess<ProductDto>> update(
        @PathVariable("productId") Long id,
        @RequestBody ProductDto productDto) {
        ProductDto updated = productService.update(id, productDto);
        return ResponseEntity.ok(ApiSuccess.of(updated, null));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiSuccess<ProductDto>> findOne(@PathVariable("productId") Long id) {
        ProductDto findProduct = productService.findById(id);
        return ResponseEntity.ok(ApiSuccess.of(findProduct, null));
    }

    @GetMapping
    public ResponseEntity<ApiSuccess<Page<ProductDto>>> find(
        @ModelAttribute ProductSearchCondition productSearchCond,
        Pageable pageable) {
        Page<ProductDto> findProducts = productService.findAll(productSearchCond, pageable);
        return ResponseEntity.ok(ApiSuccess.of(findProducts, null));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(@PathVariable("productId") Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{productId}/stock/v1/decrease-by-order")
    @Deprecated
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrderV1(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // 재고 감소
        StockResult stockResult = inventoryService.decreaseByOrder(productId, orderRequest.getOrderId(), orderRequest.getQuantity(), idempotencyKey);

        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));

    }

    @PatchMapping("/{productId}/stock/v2/decrease-by-order")
    @Deprecated
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrderV2(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // 재고 감소
        StockResult stockResult = inventoryService.decreaseByOrderV2(productId, orderRequest.getOrderId(), orderRequest.getQuantity(), idempotencyKey);

        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));

    }

    @PatchMapping("/{productId}/stock/v3/decrease-by-order")
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrder(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        // 재고 감소
        StockResult stockResult = inventoryServiceV2.decreaseByOrder(productId, orderRequest.getOrderId(), orderRequest.getQuantity(), idempotencyKey);

        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));
    }

    @PatchMapping("/{productId}/stock/increase-by-order")
    public ResponseEntity<ApiSuccess<StockResult>> increaseByOrder(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // 재고 증가
        StockResult stockResult = inventoryService.increaseByOrder(productId, orderRequest.getOrderId(), orderRequest.getQuantity(), idempotencyKey);

        // 원장 기록
        stockLedgerService.save(productId, Direction.IN, orderRequest.getQuantity(), orderRequest.getOrderId(), idempotencyKey);
        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));

    }

}
