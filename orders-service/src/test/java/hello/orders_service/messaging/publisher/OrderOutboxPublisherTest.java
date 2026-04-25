package hello.orders_service.messaging.publisher;

import hello.orders_service.order.infra.config.OrderRabbitConfig;
import hello.orders_service.order.infra.messaging.publisher.OrderOutboxPublisher;
import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.outbox.OutboxStatus;
import hello.orders_service.order.repository.OrderOutboxRepository;
import hello.orders_service.order.service.OrderOutboxService;
import hello.orders_service.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderOutboxPublisherTest {

    @Mock
    private OrderOutboxRepository orderOutboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private OrderOutboxService orderOutboxService;

    @Mock
    private OrderService orderService;

    private OrderOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        this.publisher = new OrderOutboxPublisher(orderOutboxRepository, rabbitTemplate, orderOutboxService, orderService);
    }

    @Test
    void pending_발행_성공_시_sent_전이() {
        // given
        Long outboxId = 1L;
        String payloadJson = "{\"orderId\":100,\"productId\":500,\"quantity\":2,\"requestId\":\"DEC-123\"}";

        OrderOutbox outbox = OrderOutbox.pending(100L, "STOCK_DECREASE_REQUEST", payloadJson);
        ReflectionTestUtils.setField(outbox, "id", outboxId);
        ReflectionTestUtils.setField(outbox, "status", OutboxStatus.PENDING);

        when(orderOutboxRepository.findTop100ByStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            eq(List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)),
            eq(5),
            any(LocalDateTime.class)
        )).thenReturn(List.of(outbox));

        // when
        publisher.publishDueOutboxEvents();

        // then
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, times(1)).send(
            eq(OrderRabbitConfig.STOCK_REQUEST_EXCHANGE),
            eq(OrderRabbitConfig.STOCK_REQUEST_ROUTING_KEY),
            msgCaptor.capture()
        );

        Message sentMessage = msgCaptor.getValue();
        assertThat(sentMessage.getMessageProperties().getContentType())
            .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(new String(sentMessage.getBody(), StandardCharsets.UTF_8)).isEqualTo(payloadJson);

        verify(orderOutboxService, times(1)).markSent(outboxId);
        verify(orderOutboxService, never()).markFailed(anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void unsupported_eventType_발행_시_failed_전이() {
        // given
        Long outboxId = 2L;
        OrderOutbox outbox = OrderOutbox.pending(101L, "UNSUPPORTED_EVENT_TYPE", "{\"any\":\"json\"}");
        ReflectionTestUtils.setField(outbox, "id", outboxId);

        when(orderOutboxRepository.findTop100ByStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            eq(List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)),
            eq(5),
            any(LocalDateTime.class)
        )).thenReturn(List.of(outbox));

        // when
        publisher.publishDueOutboxEvents();

        // then
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
        verify(orderOutboxService, times(1)).markFailed(eq(outboxId), anyString(), any(LocalDateTime.class));
        verify(orderOutboxService, never()).markSent(anyLong());
    }

    @Test
    void 최대_재시도_도달_시_dead_전이와_주문실패처리() {
        // given
        Long outboxId = 3L;
        OrderOutbox outbox = OrderOutbox.pending(102L, "UNSUPPORTED_EVENT_TYPE", "{\"any\":\"json\"}");
        ReflectionTestUtils.setField(outbox, "id", outboxId);
        ReflectionTestUtils.setField(outbox, "retryCount", 4);

        when(orderOutboxRepository.findTop100ByStatusInAndRetryCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            eq(List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)),
            eq(5),
            any(LocalDateTime.class)
        )).thenReturn(List.of(outbox));

        // when
        publisher.publishDueOutboxEvents();

        // then
        verify(orderOutboxService).markDead(eq(outboxId), anyString());
        verify(orderService).failOrderIfPending(102L, "OUTBOX_RETRY_EXHAUSTED");
        verify(orderOutboxService, never()).markFailed(eq(outboxId), anyString(), any(LocalDateTime.class));
    }
}
