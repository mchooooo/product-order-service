package hello.orders_service.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRabbitConfig {
    // 상품 서버로 요청을 보낼 설정
    public static final String STOCK_REQUEST_EXCHANGE = "stock.request.exchange";
    public static final String STOCK_REQUEST_ROUTING_KEY = "stock.request.key";

    // 상품 서버로부터 결과를 받을 설정
    public static final String ORDER_RESULT_QUEUE = "order.result.queue";
    public static final String ORDER_RESULT_EXCHANGE = "order.result.exchange";
    public static final String ORDER_RESULT_ROUTING_KEY = "order.result.key";

    @Bean
    public TopicExchange stockRequestExchange() {
        return new TopicExchange(STOCK_REQUEST_EXCHANGE);
    }

    @Bean
    public Queue orderResultQueue() {
        return new Queue(ORDER_RESULT_QUEUE);
    }

    @Bean
    public Binding bindingOrderResult() {
        return BindingBuilder.bind(orderResultQueue())
            .to(new TopicExchange(ORDER_RESULT_EXCHANGE))
            .with(ORDER_RESULT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
