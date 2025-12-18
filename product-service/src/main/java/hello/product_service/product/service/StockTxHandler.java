package hello.product_service.product.service;

import hello.product_service.product.domain.Direction;
import hello.product_service.product.domain.Product;
import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.exception.ProductNotFoundException;
import hello.product_service.product.model.StockResult;
import hello.product_service.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StockTxHandler {
    private final ProductRepository productRepository;
    private final StockLedgerService stockLedgerService;

    /**
     * DB에 최종적으로 반영하고 원장 기록을 남기는 메서드.
     */
    @Transactional
    public StockResult finalizeStockDecreaseInDB(Long productId, int quantity, Long orderId, String requestId) {

        // 1. DB 재고 최종 차감 (단일 UPDATE 쿼리 사용)
        int updated = productRepository.decrement(productId, quantity);
        Product updatedProduct = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));

        if (updated == 1) {
            // 2. 차감 성공, 재고 원장 기록
            stockLedgerService.save(productId, Direction.OUT, quantity, orderId, requestId);
            return new StockResult(true, updatedProduct.getStock(), "OK");
        } else {
            // 3. DB 재고 부족 발생 시, 트랜잭션 롤백 및 예외 발생 유도
            throw new InsufficientStockException(productId, updatedProduct.getStock());
        }
    }

}
