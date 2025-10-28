package hello.product_service.product.service;

import hello.product_service.product.domain.ProductStatus;
import hello.product_service.product.exception.ProductNotFoundException;
import hello.product_service.product.model.ProductCreateRequest;
import hello.product_service.product.model.ProductDto;
import hello.product_service.product.model.ProductSearchCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ProductServiceTest {
//    @Autowired
//    ProductRepository productRepository;
//    @Autowired
//    ProductSearchRepository searchRepository;
    @Autowired
    ProductService productService;


    @Test
    void findAllTest() {
        //given
        ProductSearchCondition cond = new ProductSearchCondition();

        //when
        Page<ProductDto> products = productService.findAll(cond, Pageable.unpaged());

        //then
        assertThat(products.getSize()).isEqualTo(2);
    }

    @Test
    void pagingFindAllTest() {
        //given
        ProductSearchCondition cond = new ProductSearchCondition();
        Pageable pageable = Pageable.ofSize(1);

        //when
        Page<ProductDto> products = productService.findAll(cond, pageable);

        //then
        assertThat(products.getSize()).isEqualTo(1);
    }

    @Test
    void condSearchTest() {
        //given
        ProductSearchCondition cond = new ProductSearchCondition();
        cond.setName("movie");
        Pageable pageable = Pageable.unpaged();

        //when
        Page<ProductDto> products = productService.findAll(cond, pageable);

        //then
        assertThat(products.getContent().get(0).getName()).isEqualTo("movie");
    }

    @Test
    void findByIdTest() {
        //given
        Long id = 1L;

        // when
        ProductDto findProduct = productService.findById(id);

        //then
        assertThat(findProduct).isNotNull();
    }

    @Test
    void createTest() {
        //given
        ProductCreateRequest productCreateRequest = new ProductCreateRequest("test", 10000, 10);

        //when
        Long createdId = productService.create(productCreateRequest);
        ProductDto findProduct = productService.findById(createdId);

        //then
        assertThat(findProduct.getName()).isEqualTo(productCreateRequest.getName());
        assertThat(findProduct.getPrice()).isEqualTo(productCreateRequest.getPrice());
        assertThat(findProduct.getStock()).isEqualTo(productCreateRequest.getStock());
    }

    @Test
    void updateTest() {

        //given
        ProductDto productDto = new ProductDto("test", 20000, 20, ProductStatus.ACTIVE);
        Long id = 1L;

        //when
        ProductDto updated = productService.update(id, productDto);

        //then
        assertThat(updated.getName()).isEqualTo(productDto.getName());
        assertThat(updated.getPrice()).isEqualTo(productDto.getPrice());
        assertThat(updated.getStock()).isEqualTo(productDto.getStock());
    }

    @Test
    void deleteTest() {
        //given
        Long id = 1L;

        //when
        productService.delete(id);
        ProductDto findProduct = productService.findById(id);

        //then
        assertThat(findProduct.getProductStatus()).isEqualTo(ProductStatus.DELETE);

    }
}