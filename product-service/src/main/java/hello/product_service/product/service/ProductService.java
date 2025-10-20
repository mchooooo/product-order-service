package hello.product_service.product.service;

import hello.product_service.product.domain.Product;
import hello.product_service.product.model.ProductCreateRequest;
import hello.product_service.product.model.ProductDto;
import hello.product_service.product.model.ProductSearchCondition;
import hello.product_service.product.repository.ProductRepository;
import hello.product_service.product.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;

    @Transactional
    public Long create(ProductCreateRequest productCreateRequest) {
        Product product = ProductCreateRequest.fromDto(productCreateRequest);
        productRepository.save(product);
        log.info("save product, id = {}", product.getId());
        return product.getId();
    }

    @Transactional
    public ProductDto update(Long id, ProductDto productDto) {
        Product findProduct = productRepository.findById(id).orElseThrow();
        findProduct.update(productDto);
        return ProductDto.of(findProduct);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> findAll(ProductSearchCondition cond, Pageable pageable) {
        Page<Product> products = searchRepository.search(cond, pageable);
        return products.map(ProductDto::of);
    }

    @Transactional(readOnly = true)
    public ProductDto findById(Long id) {
        return ProductDto.of(productRepository.findById(id).orElseThrow());
    }

    @Transactional
    public void delete(Long id) {
        Product findProduct = productRepository.findById(id).orElseThrow();
        findProduct.delete();
        return;
    }
}
