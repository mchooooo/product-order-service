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

    // 💡 1. 결과를 받을 Exchange를 반드시 Bean으로 등록해야 합니다!
    @Bean
    public TopicExchange orderResultExchange() {
        return new TopicExchange(ORDER_RESULT_EXCHANGE);
    }

    @Bean
    public Queue orderResultQueue() {
        return new Queue(ORDER_RESULT_QUEUE);
    }

    // 💡 2. 바인딩 시 위에서 선언한 Bean을 참조하도록 수정합니다.
    @Bean
    public Binding bindingOrderResult(Queue orderResultQueue, TopicExchange orderResultExchange) {
        return BindingBuilder.bind(orderResultQueue)
            .to(orderResultExchange)
            .with(ORDER_RESULT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
