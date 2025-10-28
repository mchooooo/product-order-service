package hello.product_service.product.service;

import hello.product_service.product.domain.Direction;
import hello.product_service.product.domain.Product;
import hello.product_service.product.domain.Reason;
import hello.product_service.product.domain.StockLedger;
import hello.product_service.product.exception.ProductNotFoundException;
import hello.product_service.product.repository.ProductRepository;
import hello.product_service.product.repository.StockLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockLedgerService {
    private final StockLedgerRepository stockLedgerRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Long save(Long productId, Direction direction, int quantity, Long orderId, String requestId) {
        Product findProduct = productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        Reason reason = direction == Direction.OUT ? Reason.ORDER_DECREMENT : Reason.ORDER_INCREMENT;
        StockLedger stockLedger = StockLedger.create(findProduct, direction, reason, quantity, orderId, requestId);
        stockLedgerRepository.save(stockLedger);

        return stockLedger.getId();
    }
}
