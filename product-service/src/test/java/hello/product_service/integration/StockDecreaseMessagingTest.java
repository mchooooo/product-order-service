package hello.product_service.integration;

import hello.product_service.product.infra.TestContainerInitializer;
import hello.product_service.product.infra.rabbitmq.StockResultProducer;
import hello.product_service.product.infra.redis.StockRedisManager;
import hello.product_service.product.model.event.StockDecreaseEvent;
import hello.product_service.product.model.event.StockResultEvent;
import hello.product_service.product.util.config.RabbitMqConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ContextConfiguration(initializers = TestContainerInitializer.class)
class StockDecreaseMessagingTest {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StockRedisManager stockRedisManager;

    @MockitoBean
    private StockResultProducer resultProducer; // 결과 응답 발송만 Mock

    @Test
    void stock_decrease_integration_test() {
        // given
        Long productId = 1L;
        int initialStock = 100;
        int quantity = 5;
        stockRedisManager.initializeStock(productId, initialStock);

        StockDecreaseEvent event = new StockDecreaseEvent(
            123L, productId, quantity, "REQ_MSG_001"
        );

        // when: 리스너 메서드를 직접 호출하지 않고, 실제 Exchange로 메시지 발행
        rabbitTemplate.convertAndSend(
            RabbitMqConfig.STOCK_REQUEST_EXCHANGE,
            RabbitMqConfig.STOCK_REQUEST_ROUTING_KEY,
            event
        );

        // then: 비동기 처리를 위해 Awaitility로 최대 5초간 대기하며 검증
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                // Redis 재고가 95로 줄었는지 확인
                assertThat(stockRedisManager.findStock(productId)).isEqualTo(95);

                // 주문 서버에 보낼 응답이 성공으로 생성되었는지 확인
                verify(resultProducer).sendResult(argThat(StockResultEvent::isSuccess));
            });
    }
}
