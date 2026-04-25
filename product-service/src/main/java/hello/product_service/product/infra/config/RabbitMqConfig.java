package hello.product_service.product.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    public static final String STOCK_REQUEST_DLX = "stock.request.dlx";
    public static final String STOCK_REQUEST_DLQ = "stock.request.dlq";
    public static final String STOCK_REQUEST_DLQ_ROUTING_KEY = "stock.request.dlq.key";

    // 결과를 주문 서버로 돌려줄 설정
    public static final String ORDER_RESULT_EXCHANGE = "order.result.exchange";
    public static final String ORDER_RESULT_ROUTING_KEY = "order.result.key";

    // 상품 서버가 메시지를 받기 위해 필요한 큐 생성
    @Bean
    public Queue stockRequestQueue() {
        return QueueBuilder.durable(STOCK_REQUEST_QUEUE)
            .withArgument("x-dead-letter-exchange", STOCK_REQUEST_DLX)
            .withArgument("x-dead-letter-routing-key", STOCK_REQUEST_DLQ_ROUTING_KEY)
            .build();
    }

    @Bean
    public Queue stockRequestDeadLetterQueue() {
        return QueueBuilder.durable(STOCK_REQUEST_DLQ).build();
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
    public TopicExchange stockRequestDeadLetterExchange() {
        return new TopicExchange(STOCK_REQUEST_DLX);
    }

    @Bean
    public Binding stockRequestDeadLetterBinding() {
        return BindingBuilder.bind(stockRequestDeadLetterQueue())
            .to(stockRequestDeadLetterExchange())
            .with(STOCK_REQUEST_DLQ_ROUTING_KEY);
    }

    @Bean
    public TopicExchange orderResultExchange() {
        return new TopicExchange(ORDER_RESULT_EXCHANGE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory stockRequestListenerContainerFactory(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
