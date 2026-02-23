package hello.product_service.product.infra.redis;

import hello.product_service.product.infra.TestContainerInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


@SpringBootTest
@ContextConfiguration(initializers = TestContainerInitializer.class) // 초기화 클래스 지정
class StockRedisManagerTest {

    @MockitoSpyBean
    StockRedisManagerV2 stockRedisManager;

    private static final Long PRODUCT_ID = 999L;
    private static final int INITIAL_STOCK = 50;

    @BeforeEach
    void setup() {
        // 매 테스트 시작 전 Redis 초기화
        stockRedisManager.initializeStock(PRODUCT_ID, INITIAL_STOCK);
    }

    @Test
    void reserveStock_정상감소() {
        // given
        int quantity = 10;

        // when
        Long remainingStock = stockRedisManager.reserveStock(PRODUCT_ID, quantity);

        // then
        assertThat(remainingStock).isEqualTo(INITIAL_STOCK - quantity);
        assertThat(stockRedisManager.findStock(PRODUCT_ID)).isEqualTo(40L);
    }

    @Test
    void reserveStock_재고부족() {
        // given
        int quantity = 60;

        // when
        Long remainingStock = stockRedisManager.reserveStock(PRODUCT_ID, quantity);

        // then
        assertThat(remainingStock).isEqualTo(-1L);
        assertThat(stockRedisManager.findStock(PRODUCT_ID)).isEqualTo(50L); // 변경되지 않음
    }

    @Test
    void restoreStock_재고복구() {
        // given
        // 10개 선점
        int quantity = 10;
        Long remainingStock = stockRedisManager.reserveStock(PRODUCT_ID, quantity); //40개 남

        // when
        // 10개 복구
        stockRedisManager.restoreStock(PRODUCT_ID, quantity);

        // then
        assertThat(remainingStock).isEqualTo(40);
        assertThat(stockRedisManager.findStock(PRODUCT_ID)).isEqualTo(INITIAL_STOCK);
    }

    @Test
    void fallbackTest() {
        // given
        when(stockRedisManager.reserveStock(999L, 10))
            .thenThrow(new RedisConnectionFailureException("conn failed"));

        // when
        Long result = stockRedisManager.reserveStock(PRODUCT_ID, 10);

        // then
        assertThat(result).isEqualTo(-999L);
    }
}