package hello.product_service.product.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockRedisManager {
    private final RedisTemplate<String, String> redisTemplate;
    private static final String REDIS_STOCK_KEY_PREFIX = "stock:";

    /**
     * Redis DECRBY를 이용한 재고 선점
     * 재고가 부족하면 복구(INCRBY) 후 실패(-1L)를 반환
     * @return 감소 후 남은 재고 수량. 실패 시 -1L.
     */
    public Long reserveStock(Long productId, int quantity) {
        String key = REDIS_STOCK_KEY_PREFIX + productId;

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

}
