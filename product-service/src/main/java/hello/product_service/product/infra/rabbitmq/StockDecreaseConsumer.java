package hello.product_service.product.infra.rabbitmq;

import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.model.event.StockDecreaseEvent;
import hello.product_service.product.model.event.StockResultEvent;
import hello.product_service.product.service.InventoryServiceV2;
import hello.product_service.product.util.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockDecreaseConsumer {
    private final InventoryServiceV2 inventoryService;
    private final StockResultProducer stockResultProducer;

    @RabbitListener(queues = RabbitMqConfig.STOCK_REQUEST_QUEUE)
    public void handleStockDecrease(StockDecreaseEvent event) {
        log.info("재고 차감 요청 수신 - 주문ID: {}, 상품ID: {}, 수량: {}", event.getOrderId(), event.getProductId(), event.getQuantity());
        try {
            // 재고 감소 실행
            inventoryService.decreaseByOrder(
                event.getProductId(),
                event.getOrderId(),
                event.getQuantity(),
                event.getRequestId()
            );

            // 성공 응답 전송
            stockResultProducer.sendResult(new StockResultEvent(event.getOrderId(), true, null));

        } catch (InsufficientStockException e) {
            log.warn("재고 부족 - 주문ID: {}", event.getOrderId());
            stockResultProducer.sendResult(new StockResultEvent(event.getOrderId(), false, "INSUFFICIENT_STOCK"));

        } catch (Exception e) {
            log.error("시스템 장애 - 주문ID: {}", event.getOrderId(), e);
            stockResultProducer.sendResult(new StockResultEvent(event.getOrderId(), false, "SYSTEM_ERROR"));
        }
    }


}
