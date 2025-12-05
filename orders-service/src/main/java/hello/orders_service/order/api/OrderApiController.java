package hello.orders_service.order.api;

import hello.orders_service.common.ApiSuccess;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.dto.request.OrderCreateRequest;
import hello.orders_service.order.dto.response.OrderResponse;
import hello.orders_service.order.saga.OrderSagaOrchestrator;
import hello.orders_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderApiController {
    private final OrderService orderService;
    private final OrderSagaOrchestrator orderSagaOrchestrator;


    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiSuccess<OrderResponse>> findById(@PathVariable("orderId") Long orderId) {
        Order findOrder = orderService.findById(orderId);
        OrderResponse response = OrderResponse.from(findOrder);
        ApiSuccess<OrderResponse> result = ApiSuccess.of(response, null);

        return ResponseEntity.ok(result);
    }
    // 2-1. 주문 생성

    /**
     * V1 OrderService 사용
     * 상품 서버와 주문 서버의 일관성을 고려하지 않음.
     */
    @PostMapping("/orders/v1")
    public ResponseEntity<ApiSuccess<OrderResponse>> createV1(@RequestBody OrderCreateRequest req) {
        Order createOrder = orderService.create(req.getProductId(), req.getBuyerId() ,req.getQuantity());

        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(createOrder), null)); // 최종 상태(PENDING→CONFIRMED/FAILED) 반영
    }

    // 2-2. 주문 취소
    /**
     * V1 OrderService 사용
     * 상품 서버와 주문 서버의 일관성을 고려하지 않음.
     */
    @PostMapping("/orders/{orderId}/v1")
    public ResponseEntity<ApiSuccess<OrderResponse>> cancelV1(@PathVariable Long orderId) {
        Order cancelOrder = orderService.cancel(orderId);
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(cancelOrder), null));
    }

    /**
     * V2 OrderSagaOrchestrator 사용
     * 상품 서버와 주문 서버의 일관성 고려
     */
    @PostMapping("/orders")
    public ResponseEntity<ApiSuccess<OrderResponse>> create(@RequestBody OrderCreateRequest req) {
        Order createOrder = orderSagaOrchestrator.startOrder(req.getProductId(), req.getBuyerId() ,req.getQuantity());

        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(createOrder), null)); // 최종 상태(PENDING→CONFIRMED/FAILED) 반영
    }

    /**
     * V2 OrderSagaOrchestrator 사용
     * 상품 서버와 주문 서버의 일관성 고려
     */
    @PostMapping("/orders/{orderId}")
    public ResponseEntity<ApiSuccess<OrderResponse>> cancel(@PathVariable Long orderId) {
        Order cancelOrder = orderSagaOrchestrator.startCancel(orderId);
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(cancelOrder), null));
    }

}
