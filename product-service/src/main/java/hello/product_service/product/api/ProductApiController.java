package hello.product_service.product.api;

import hello.product_service.common.ApiSuccess;
import hello.product_service.product.model.*;
import hello.product_service.product.service.InventoryService;
import hello.product_service.product.service.ProductService;
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

    @PatchMapping("/{productId}/stock/decrease-by-order")
    public ResponseEntity<ApiSuccess<StockResult>> decreaseByOrder(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        StockResult stockResult = inventoryService.decreaseByOrder(productId, orderRequest.getOrderId(), orderRequest.getQuantity(), idempotencyKey);
        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));

    }

    @PatchMapping("/{productId}/stock/increase-by-order")
    public ResponseEntity<ApiSuccess<StockResult>> increaseByOrder(
        @PathVariable("productId") Long productId,
        @RequestBody OrderRequest orderRequest,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        StockResult stockResult = inventoryService.increaseByOrder(productId, orderRequest.getOrderId(), orderRequest.getQuantity(), idempotencyKey);
        return ResponseEntity.ok(ApiSuccess.of(stockResult, null));

    }

}
