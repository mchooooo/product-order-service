package hello.product_service.product.api.v1;

import hello.product_service.common.web.response.ApiSuccess;
import hello.product_service.product.model.ProductCreateRequest;
import hello.product_service.product.model.ProductDto;
import hello.product_service.product.model.ProductSearchCondition;
import hello.product_service.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/v1/products")
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiSuccess<ProductDto>> create(@RequestBody ProductCreateRequest productDto) {
        Long createdId = productService.create(productDto);
        ProductDto createdProduct = productService.findById(createdId);

        return ResponseEntity.ok(ApiSuccess.of(createdProduct, null));
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<ApiSuccess<ProductDto>> update(
        @PathVariable("productId") Long id,
        @RequestBody ProductDto productDto
    ) {
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
        Pageable pageable
    ) {
        Page<ProductDto> findProducts = productService.findAll(productSearchCond, pageable);
        return ResponseEntity.ok(ApiSuccess.of(findProducts, null));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> delete(@PathVariable("productId") Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

