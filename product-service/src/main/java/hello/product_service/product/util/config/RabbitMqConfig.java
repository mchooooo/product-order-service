package hello.product_service.product.util.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    // 주문 서버가 보내는 요청을 받을 설정 (주문 서버의 설정과 일치해야함)
    public static final String STOCK_REQUEST_EXCHANGE = "stock.request.exchange";
    public static final String STOCK_REQUEST_ROUTING_KEY = "stock.request.key";
    public static final String STOCK_REQUEST_QUEUE = "stock.request.queue"; // 상품 서버에서 사용할 큐 이름

    // 결과를 주문 서버로 돌려줄 설정
    public static final String ORDER_RESULT_EXCHANGE = "order.result.exchange";
    public static final String ORDER_RESULT_ROUTING_KEY = "order.result.key";

    // 상품 서버가 메시지를 받기 위해 필요한 큐 생성
    @Bean
    public Queue stockRequestQueue() {
        return new Queue(STOCK_REQUEST_QUEUE, true);
    }

    // 주문 서버의 Exchange와 상품 서버의 큐 연결
    @Bean
    public Binding stockRequestBinding() {
        return BindingBuilder.bind(stockRequestQueue())
            .to(stockRequestExchange())
            .with(STOCK_REQUEST_ROUTING_KEY);
    }

    @Bean
    public TopicExchange stockRequestExchange() {
        return new TopicExchange(STOCK_REQUEST_EXCHANGE);
    }

    @Bean
    public TopicExchange orderResultExchange() {
        return new TopicExchange(ORDER_RESULT_EXCHANGE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
