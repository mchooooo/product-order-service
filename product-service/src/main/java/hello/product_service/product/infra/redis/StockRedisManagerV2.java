package hello.product_service.product.infra.redis;

import hello.product_service.product.repository.ProductRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
/**
 * Resilience4j 사용
 * 레디스 다운되었을 경우 상품서버 장애 발생 -> 레디스가 죽으면 DB 로직 수행 (폴백 로직) 구현
 */
public class StockRedisManagerV2 {
    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private static final String REDIS_STOCK_KEY_PREFIX = "stock:";

    /**
     * Redis DECRBY를 이용한 재고 선점
     * 재고가 부족하면 복구(INCRBY) 후 실패(-1L)를 반환
     * 레디스 장애시 폴백 메서드 실행 후 (-999L) 반환
     * @return 감소 후 남은 재고 수량. 실패 시 -1L, 레디스 장애 시 -999L
     */
    @CircuitBreaker(name = "redisStockBreaker", fallbackMethod = "fallbackForRedis")
    public Long reserveStock(Long productId, int quantity) {
        String key = REDIS_STOCK_KEY_PREFIX + productId;

        // 서킷 브레이커 가져옴
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker
            = circuitBreakerRegistry.circuitBreaker("redisStockBreaker");

        // 상태 확인, HALF_OPEN(복구 시도 중) 경우 DB와 동기화
        if (circuitBreaker.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.HALF_OPEN) {
            log.info("서킷 상태가 {}이거나 키가 없어 DB 동기화를 진행합니다. (상품ID: {})", circuitBreaker.getState(), productId);
            refreshStockFromDB(productId);
        }


        // Redis의 DECRBY 감소 후 값을 반환
        Long remainingStock = redisTemplate.opsForValue().decrement(key, quantity);
        log.info("REDIS DECRBY, key = {}, remainingStock = {}", key, remainingStock);

        if (remainingStock != null && remainingStock < 0) {
            // 재고 부족 시 롤백, quantity만큼 증가
            redisTemplate.opsForValue().increment(key, quantity);
            return -1L; // 재고 부족 실패 코드
        }

        return remainingStock; // 성공 시 남은 재고 반환
    }

    // DB에서 최신 재고를 읽어 Redis에 갱신하는 메서드
    private void refreshStockFromDB(Long productId) {
        // DB 재고 조회 (실제로는 Product 엔티티의 stock 필드)
        int dbStock = productRepository.findStockById(productId);
        redisTemplate.opsForValue().set("stock:" + productId, String.valueOf(dbStock));
        log.info("Redis 재고 동기화 완료: 상품 {} -> 재고 {}개", productId, dbStock);
    }


    /**
     * DB 트랜잭션 실패 등 예외 발생 시 Redis 재고 복구 (Compensating Transaction)
     * @return 복구 후 재고 수량.
     */
    public Long restoreStock(Long productId, int quantity) {
        String key = REDIS_STOCK_KEY_PREFIX + productId;
        log.info("REDIS INCRBY, key = {}, quantity = {}", key, quantity);

        // INCRBY를 통해 재고 증가
        return redisTemplate.opsForValue().increment(key, quantity);
    }

    /**
     * Redis 초기화
     */
    public void initializeStock(Long productId, int initialStock) {
        String key = REDIS_STOCK_KEY_PREFIX + productId;
        log.info("REDIS initialize, key = {}, productId = {}, initialStock = {}", key, productId, initialStock);
        redisTemplate.opsForValue().set(key, String.valueOf(initialStock));
    }

    public Long findStock(Long productId) {
        String key = REDIS_STOCK_KEY_PREFIX + productId;

        // 1. Redis에서 문자열 값을 가져옴.
        String stockStr = redisTemplate.opsForValue().get(key);

        // 2. 값이 null이거나 없으면 0을 반환.
        if (!StringUtils.hasText(stockStr)) {
            return 0L;
        }
        log.info("REDIS find stock, key = {}, stock = {}", key, stockStr);
        return Long.parseLong(stockStr);
    }

    // Redis 장애 시 호출될 폴백 (Exception을 인자로 받아야 함)
    public Long fallbackForRedis(Long productId, int quantity, Throwable t) {
        log.error("Redis 연결 실패! 서킷 브레이커 작동. DB 직접 처리로 전환합니다. 사유: {}", t.getMessage());
        return -999L; // Redis 장애를 알리는 특수 코드
    }

}
