package hello.orders_service.order.api.v2;

import hello.orders_service.common.web.response.ApiSuccess;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.dto.request.OrderCreateRequest;
import hello.orders_service.order.dto.response.OrderResponse;
import hello.orders_service.order.saga.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderV2Controller {
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    /**
     * V2 OrderSagaOrchestrator 사용
     * 상품 서버와 주문 서버의 일관성 고려
     * 사가패턴 적용
     */
    @PostMapping("/v2/orders")
    public ResponseEntity<ApiSuccess<OrderResponse>> create(@RequestBody OrderCreateRequest req) {
        Order createOrder = orderSagaOrchestrator.startOrder(req.getProductId(), req.getBuyerId(), req.getQuantity());
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(createOrder), null));
    }

    /**
     * V2 OrderSagaOrchestrator 사용
     * 상품 서버와 주문 서버의 일관성 고려
     * 사가패턴 적용
     */
    @PostMapping("/v2/orders/{orderId}")
    public ResponseEntity<ApiSuccess<OrderResponse>> cancel(@PathVariable Long orderId) {
        Order cancelOrder = orderSagaOrchestrator.startCancel(orderId);
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(cancelOrder), null));
    }
}

