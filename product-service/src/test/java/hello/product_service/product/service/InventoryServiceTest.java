package hello.product_service.product.service;

import hello.product_service.product.domain.Product;
import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.model.StockResult;
import hello.product_service.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InventoryServiceTest {
    @Autowired
    InventoryService inventoryService;

    @Autowired
    ProductRepository productRepository;

    @Test
    void decreaseByOrderTest() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 20;
        String requestId = "test";

        //when
        StockResult result = inventoryService.decreaseByOrder(productId, orderId, quantity, requestId);
        Product findProduct = productRepository.findById(productId).orElseThrow();

        //then
        assertThat(result.getRemainingStock()).isEqualTo(findProduct.getStock());
    }

    @Test
    void decreaseByOrderTest_idempotency() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 20;
        String requestId = "test";

        //when
        StockResult firstResult = inventoryService.decreaseByOrder(productId, orderId, quantity, requestId);
        StockResult secondResult = inventoryService.decreaseByOrder(productId, orderId, quantity, requestId);

        //then
        assertThat(firstResult).isEqualTo(secondResult);
    }

    @Test
    void decreaseByOrderTest_차감실패() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 200;
        String requestId = "test";


        assertThatThrownBy(
            ()-> inventoryService.decreaseByOrder(productId, orderId, quantity, requestId))
            .isInstanceOf(InsufficientStockException.class);


    }


    @Test
    void increaseByOrderTest() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 20;
        String requestId = "test";

        //when
        StockResult result = inventoryService.increaseByOrder(productId, orderId, quantity, requestId);
        Product findProduct = productRepository.findById(productId).orElseThrow();

        //then
        assertThat(result.getRemainingStock()).isEqualTo(findProduct.getStock());
    }

    @Test
    void increaseByOrderTest_idempotency() {
        //given
        Long productId = 1L;
        Long orderId = 1L;
        int quantity = 20;
        String requestId = "test";

        //when
        StockResult firstResult = inventoryService.increaseByOrder(productId, orderId, quantity, requestId);
        StockResult secondResult = inventoryService.increaseByOrder(productId, orderId, quantity, requestId);

        //then
        assertThat(firstResult).isEqualTo(secondResult);
    }
}