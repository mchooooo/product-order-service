package hello.product_service.product.service;

import hello.product_service.product.domain.IdempotencyRecord;
import hello.product_service.product.domain.IdempotencyStatus;
import hello.product_service.product.domain.StockStrategy;
import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.infra.redis.StockRedisManager;
import hello.product_service.product.infra.redis.StockRedisManagerV2;
import hello.product_service.product.model.StockResult;
import hello.product_service.product.repository.IdempotencyRepository;
import hello.product_service.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
/**
 * InventoryService에서 구현한 재고 감소 기능 고도화
 * 트래픽이 많이 몰릴 것으로 예상되는 상품은 데이터베이스의 부하를 덜기 위한 Redis 도입
 */
public class InventoryServiceV2 {
    private final ProductRepository productRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final StockRedisManagerV2 stockRedisManager;
    private final StockTxHandler stockTxHandler;

    /**
     * 멱등성 체크 및 재고 전략 분기
     */
    public StockResult decreaseByOrder(Long productId, Long orderId, int quantity, String requestId) {
        // 1. 멱등성 조회 (중복 요청 차단)
        IdempotencyRecord cached = idempotencyRepository.findByRequestId(requestId).orElse(null);
        if (cached != null) {
            return StockResult.create(cached);
        }

        StockResult stockResult = null;

        // 2. 상품의 재고 처리 전략 조회
        StockStrategy strategy = productRepository.findStockStrategyById(productId);



        if (strategy == StockStrategy.REDIS_FIRST) {
            // 3. 인기 상품 처리 로직 (Redis 필터링 포함)
            stockResult = processHotItemDecrease(productId, quantity, orderId, requestId);
        } else {
            // 4. 일반 상품 처리 로직 (DB-Only)
            stockResult = processNormalItemDecrease(productId, quantity, orderId, requestId);
        }

        // 5. 멱등성 저장 및 최종 결과 반환/예외 처리
        return saveIdempotencyAndReturn(stockResult, requestId);
    }


    /**
     * 인기 상품 로직: Redis 선점 -> DB 최종 반영
     */
    private StockResult processHotItemDecrease(Long productId, int quantity, Long orderId, String requestId) {
        // 1. Redis 재고 선점
        Long remainStock = stockRedisManager.reserveStock(productId, quantity);
        log.info("redis remaining stock = {}", remainStock);

        // Redis 장애 폴백 발생 시 (서킷 오픈 포함)
        if (remainStock == -999L) {
            log.warn("Redis 장애로 인해 DB 직접 차감 모드로 전환합니다. productId: {}", productId);
            // 일반 상품 로직과 동일하게 DB 단일 트랜잭션으로 처리
            return stockTxHandler.finalizeStockDecreaseInDB(productId, quantity, orderId, requestId);
        }

        if (remainStock == -1L) {
            // Redis 선점 실패
            throw new InsufficientStockException(productId, productRepository.findStockById(productId));
        }

        // 2. Redis 재고 선점 이후 DB 반영 및 실패 시 복구
        try {
            return stockTxHandler.finalizeStockDecreaseInDB(productId, quantity, orderId, requestId);
        } catch (InsufficientStockException ex) {
            // 3. DB 최종 차감 실패 (Redis-DB 불일치 등) 시 Redis 복구
            stockRedisManager.restoreStock(productId, quantity);
            throw new InsufficientStockException(productId, productRepository.findStockById(productId));
        } catch (Exception ex) {
            // 4. 기타 오류 발생 시 Redis 복구
            stockRedisManager.restoreStock(productId, quantity);
            throw new RuntimeException("DB 처리 중 시스템 오류 발생", ex);
        }
    }

    /**
     * 일반 상품 로직: DB 단일 쿼리만 사용
     */
    private StockResult processNormalItemDecrease(Long productId, int quantity, Long orderId, String requestId) {
        return stockTxHandler.finalizeStockDecreaseInDB(productId, quantity, orderId, requestId);
    }

    /**
     * 멱등성 저장 및 최종 결과 반환/예외 처리
     */
    private StockResult saveIdempotencyAndReturn(StockResult stockResult, String requestId) {
        try {
            IdempotencyStatus status = stockResult.isSuccess() ? IdempotencyStatus.SUCCESS : IdempotencyStatus.FAIL;
            IdempotencyRecord record = IdempotencyRecord.create(requestId, status, stockResult.getMessage(), stockResult.getRemainingStock());
            idempotencyRepository.save(record);
            return stockResult;

        } catch (DataIntegrityViolationException ex) {
            // concurrent save → 기존 레코드 반환
            // 드물지만 동일한 멱등성 키를 가지고 요청이 들어온 경우
            return idempotencyRepository.findByRequestId(requestId).map(StockResult::create).orElse(stockResult);
        }
    }

}
