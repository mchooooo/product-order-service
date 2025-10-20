package hello.product_service.product.service;

import hello.product_service.product.domain.*;
import hello.product_service.product.model.StockResult;
import hello.product_service.product.repository.IdempotencyRepository;
import hello.product_service.product.repository.ProductRepository;
import hello.product_service.product.repository.StockLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {
    private final IdempotencyRepository idempotencyRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final ProductRepository productRepository;

    public StockResult decreaseByOrder(Long productId, Long orderId, int quantity, String requestId) {
        // 요청 아이디가 신규 값인지 조회
        IdempotencyRecord cached = idempotencyRepository.findByRequestId(requestId).orElse(null);

        if (cached != null) { // 널이 아니면 기존에 존재
            return StockResult.create(cached);
        }

        // 차감 시작 (비즈니스 로직)
        int updated = productRepository.decrement(productId, quantity);
        StockResult stockResult = null;

        if (updated == 1) { // 차감 성공
            Product updatedProduct = productRepository.findById(productId).orElseThrow();
            StockLedger stockLedger = StockLedger.create(updatedProduct, Direction.OUT, Reason.ORDER_DECREMENT, quantity, orderId, requestId);
            stockLedgerRepository.save(stockLedger);

            stockResult = new StockResult(true, updatedProduct.getStock(), "OK");
        } else { // 차감 실패
            stockResult = new StockResult(false, null, "INSUFFICIENT_STOCK");
        }

        // 멱등성 저장 (경합 시 재조회)
        try {
            IdempotencyStatus status = stockResult.isSuccess() ? IdempotencyStatus.SUCCESS : IdempotencyStatus.FAIL;
            IdempotencyRecord idempotencyRecord = IdempotencyRecord.create(requestId, status, stockResult.getMessage(), stockResult.getRemainingStock());
            idempotencyRepository.save(idempotencyRecord);

        } catch (DataIntegrityViolationException e) { //유니크 제약 위반, 동시에 삽입될 경우
            // concurrent save → 기존 레코드 반환
            return idempotencyRepository.findByRequestId(requestId).map(StockResult::create).orElse(stockResult);
        }

        return stockResult;
    }

    public StockResult increaseByOrder(Long productId, Long orderId, int quantity, String requestId) {
        // 요청 아이디가 신규 값인지 조회
        IdempotencyRecord cached = idempotencyRepository.findByRequestId(requestId).orElse(null);

        if (cached != null) { // 널이 아니면 기존에 존재
            return StockResult.create(cached);
        }

        //비즈니스 로직 -> 주문 취소로 인한 재고 증가
        productRepository.increment(productId, quantity);
        Product updatedProduct = productRepository.findById(productId).orElseThrow();

        StockLedger stockLedger = StockLedger.create(updatedProduct, Direction.IN, Reason.ORDER_INCREMENT, quantity, orderId, requestId);
        stockLedgerRepository.save(stockLedger);

        StockResult stockResult = new StockResult(true, updatedProduct.getStock(), "OK");

        // 멱등성 저장, 경합 시 재조회
        try {
            IdempotencyStatus status = stockResult.isSuccess() ? IdempotencyStatus.SUCCESS : IdempotencyStatus.FAIL;
            IdempotencyRecord idempotencyRecord = IdempotencyRecord.create(requestId, status, stockResult.getMessage(), stockResult.getRemainingStock());
            idempotencyRepository.save(idempotencyRecord);

        } catch (DataIntegrityViolationException e) { //유니크 제약 위반

            return idempotencyRepository.findByRequestId(requestId).map(StockResult::create).orElse(stockResult);
        }

        return stockResult;
    }

}
