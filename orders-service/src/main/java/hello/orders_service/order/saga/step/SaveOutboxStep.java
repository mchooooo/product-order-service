package hello.orders_service.order.saga.step;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hello.orders_service.order.infra.config.OrderRabbitConfig;
import hello.orders_service.order.infra.messaging.event.StockDecreaseRequestEvent;
import hello.orders_service.order.outbox.OrderOutbox;
import hello.orders_service.order.repository.OrderOutboxRepository;
import hello.orders_service.order.saga.OrderSagaContext;
import hello.orders_service.saga.SagaStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 이벤트를 아웃박스에 저장하는 스텝
 * 실제 RabbitMQ 메시지 발행은 별도 @Scheduled 컴포넌트가 처리
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SaveOutboxStep implements SagaStep<OrderSagaContext> {
    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public void execute(OrderSagaContext context) {
        log.info("주문 이벤트를 아웃박스에 저장 [주문ID: {}]", context.getOrderId());

        StockDecreaseRequestEvent event = new StockDecreaseRequestEvent(
            context.getOrderId(),
            context.getProductId(),
            context.getQuantity(),
            context.getDecKey()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            OrderOutbox outbox = OrderOutbox.pending(context.getOrderId(), "STOCK_DECREASE_REQUEST", payload);
            orderOutboxRepository.save(outbox);
            log.info("재고 감소 Outbox 저장 완료 [주문ID: {}]", context.getOrderId());
        } catch (JsonProcessingException e) {
            log.error("StockDecreaseRequestEven 직렬화 실패", e);
            throw new IllegalStateException("재고 감소 이벤트 직렬화 실패", e);
        }
        
    }

    @Override
    public void compensate(OrderSagaContext context) {
        log.error("재고 감소 단계 실패 롤백 처리 [주문ID: {}]", context.getOrderId());

    }
}
