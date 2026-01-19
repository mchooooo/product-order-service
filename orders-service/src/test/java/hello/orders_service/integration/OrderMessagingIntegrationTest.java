package hello.orders_service.integration;

import hello.orders_service.messaging.config.OrderRabbitConfig;
import hello.orders_service.messaging.event.StockResultEvent;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.repository.OrderRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class OrderMessagingIntegrationTest {
    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3-management");

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    OrderRepository orderRepository;

    RabbitAdmin rabbitAdmin;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
    }

    @BeforeEach
    void setUp() {
        rabbitAdmin = new RabbitAdmin(rabbitTemplate);
        // Exchange 선언
        TopicExchange exchange = new TopicExchange(OrderRabbitConfig.ORDER_RESULT_EXCHANGE);
        rabbitAdmin.declareExchange(exchange);

        // Queue 선언
        Queue queue = new Queue(OrderRabbitConfig.ORDER_RESULT_QUEUE);
        rabbitAdmin.declareQueue(queue);

        // Binding 선언 (Exchange와 Queue 연결)
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue)
            .to(exchange)
            .with(OrderRabbitConfig.ORDER_RESULT_ROUTING_KEY));
    }

    @Test
    void MQ_integration_test() {
        // given
        Order order = orderRepository.save(Order.create(1L, "test", 3));

        // when
        StockResultEvent resultEvent = new StockResultEvent(order.getId(), true, "OK");
        rabbitTemplate.convertAndSend(
            OrderRabbitConfig.ORDER_RESULT_EXCHANGE,
            OrderRabbitConfig.ORDER_RESULT_ROUTING_KEY,
            resultEvent
        );

        // then: 비동기 처리를 기다리며 상태 변경 확인 (Awaitility 사용)
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
                assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            });
    }

}
