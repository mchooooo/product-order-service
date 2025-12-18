package hello.product_service.product.domain;

import lombok.Getter;

@Getter
public enum StockStrategy {
    DB_ONLY,    // 일반 상품: DB 단일 쿼리만 사용
    REDIS_FIRST // 인기 상품: Redis 선점 후 DB 반영 사용

}
