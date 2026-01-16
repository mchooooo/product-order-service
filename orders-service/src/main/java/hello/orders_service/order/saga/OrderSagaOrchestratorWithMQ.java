package hello.orders_service.order.saga;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.saga.step.CreateOrderPendingStep;
import hello.orders_service.order.saga.step.RabbitMqStockDecreaseStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestratorWithMQ {
    private final CreateOrderPendingStep createOrderPendingStep;
    private final RabbitMqStockDecreaseStep rabbitMqStockDecreaseStep;

    /**
     * 주문 사가 시작 : 주문 생성 -> 메시지 발행 -> 끝
     */
    @Transactional
    public Order runSaga(Long productId, String buyerId, int quantity) {
        log.info("Saga 시작 - 주문 ID: {}", buyerId);
        OrderSagaContext context = new OrderSagaContext();
        context.setProductId(productId);
        context.setBuyerId(buyerId);
        context.setQuantity(quantity);

        try {
            createOrderPendingStep.execute(context);
            rabbitMqStockDecreaseStep.execute(context);
            log.info("Saga 종료: 메시지 발행 완료");
        } catch (Exception e) {
            log.error("Saga 실패: 즉시 롤백 실행", e);
            // 메시지 발행조차 실패한 경우 로컬 DB 원복
            createOrderPendingStep.compensate(context);
            throw e;
        }

        return context.getOrder();
    }

}
