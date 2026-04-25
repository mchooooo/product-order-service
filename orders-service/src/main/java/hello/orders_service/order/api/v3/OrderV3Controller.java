package hello.orders_service.order.api.v3;

import hello.orders_service.common.web.response.ApiSuccess;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.dto.request.OrderCreateRequest;
import hello.orders_service.order.dto.response.OrderResponse;
import hello.orders_service.order.saga.OrderSagaOrchestratorWithMQ;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderV3Controller {
    private final OrderSagaOrchestratorWithMQ orderSagaOrchestratorWithMQ;

    /**
     * V3 OrderSagaOrchestratorWithMQ 사용
     * 비동기 방식 : 시스템간 결합도를 낮춤 -> 상품 서버가 장애가 발생해도 주문 서버는 MQ에 이벤트를 쌓음
     * 트래픽에 유연하게 대처 : 주문이 급격히 많이 들어온 경우 MQ에 메시지를 쌓고 상품 서버는 속도에 맞게 처리
     */
    @PostMapping("/v3/orders")
    public ResponseEntity<ApiSuccess<OrderResponse>> create(@RequestBody OrderCreateRequest req) {
        Order createOrder = orderSagaOrchestratorWithMQ.runSaga(req.getProductId(), req.getBuyerId(), req.getQuantity());
        return ResponseEntity.accepted().body(ApiSuccess.of(OrderResponse.from(createOrder), null));
    }
}

