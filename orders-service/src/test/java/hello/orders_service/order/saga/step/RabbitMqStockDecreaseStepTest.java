package hello.orders_service.order.saga.step;

import hello.orders_service.messaging.config.OrderRabbitConfig;
import hello.orders_service.messaging.event.StockDecreaseRequestEvent;
import hello.orders_service.order.saga.OrderSagaContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMqStockDecreaseStepTest {

    @Mock
    RabbitTemplate rabbitTemplate;

    @InjectMocks
    RabbitMqStockDecreaseStep rabbitMqStockDecreaseStep;

    @Test
    void message_발행_성공_테스트 () throws Exception {
        // given
        OrderSagaContext context = new OrderSagaContext();
        context.setOrderId(100L);
        context.setProductId(500L);
        context.setQuantity(2);
        context.setDecKey("DEC-123");

        // when
        rabbitMqStockDecreaseStep.execute(context);

        // then: ArgumentCaptor를 사용하여 실제 나간 메시지 내용 검증
        ArgumentCaptor<StockDecreaseRequestEvent> captor = ArgumentCaptor.forClass(StockDecreaseRequestEvent.class);

        verify(rabbitTemplate).convertAndSend(
            eq(OrderRabbitConfig.STOCK_REQUEST_EXCHANGE),
            eq(OrderRabbitConfig.STOCK_REQUEST_ROUTING_KEY),
            captor.capture()
        );

        StockDecreaseRequestEvent sentEvent = captor.getValue();
        assertThat(sentEvent.getOrderId()).isEqualTo(100L);
        assertThat(sentEvent.getQty()).isEqualTo(2);
        assertThat(sentEvent.getRequestId()).isEqualTo("DEC-123");
    }

}