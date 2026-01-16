package hello.orders_service.order.api;

import hello.orders_service.common.ApiSuccess;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.dto.request.OrderCreateRequest;
import hello.orders_service.order.dto.response.OrderResponse;
import hello.orders_service.order.saga.OrderSagaOrchestrator;
import hello.orders_service.order.saga.OrderSagaOrchestratorWithMQ;
import hello.orders_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderApiController {
    private final OrderService orderService;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final OrderSagaOrchestratorWithMQ orderSagaOrchestratorWithMQ;


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
     * 사가패턴 X
     */
    @PostMapping("/v1/orders")
    public ResponseEntity<ApiSuccess<OrderResponse>> createV1(@RequestBody OrderCreateRequest req) {
        Order createOrder = orderService.create(req.getProductId(), req.getBuyerId() ,req.getQuantity());

        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(createOrder), null)); // 최종 상태(PENDING→CONFIRMED/FAILED) 반영
    }

    // 2-2. 주문 취소
    /**
     * V1 OrderService 사용
     * 상품 서버와 주문 서버의 일관성을 고려하지 않음.
     * 사가패턴 X
     */
    @PostMapping("/v1/orders/{orderId}")
    public ResponseEntity<ApiSuccess<OrderResponse>> cancelV1(@PathVariable Long orderId) {
        Order cancelOrder = orderService.cancel(orderId);
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(cancelOrder), null));
    }

    /**
     * V2 OrderSagaOrchestrator 사용
     * 상품 서버와 주문 서버의 일관성 고려
     * 사가패턴 적용
     */
    @PostMapping("/v2/orders")
    public ResponseEntity<ApiSuccess<OrderResponse>> createV2(@RequestBody OrderCreateRequest req) {
        Order createOrder = orderSagaOrchestrator.startOrder(req.getProductId(), req.getBuyerId() ,req.getQuantity());

        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(createOrder), null)); // 최종 상태(PENDING→CONFIRMED/FAILED) 반영
    }

    /**
     * V2 OrderSagaOrchestrator 사용
     * 상품 서버와 주문 서버의 일관성 고려
     * 사가패턴 적용
     */
    @PostMapping("/v2/orders/{orderId}")
    public ResponseEntity<ApiSuccess<OrderResponse>> cancelV2(@PathVariable Long orderId) {
        Order cancelOrder = orderSagaOrchestrator.startCancel(orderId);
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(cancelOrder), null));
    }

    /**
     * V3 OrderSagaOrchestratorWithMQ 사용
     * 비동기 방식 : 시스템간 결합도를 낮춤 -> 상품 서버가 장애가 발생해도 주문 서버는 MQ에 이벤트를 쌓음
     * 트래픽에 유연하게 대처 : 주문이 급격히 많이 들어온 경우 MQ에 메시지를 쌓고 상품 서버는 속도에 맞게 처리
     */
    @PostMapping("/v3/orders")
    public ResponseEntity<ApiSuccess<OrderResponse>> createV3(@RequestBody OrderCreateRequest req) {
        // 주문 생성 사가 진행
        Order createOrder = orderSagaOrchestratorWithMQ.runSaga(req.getProductId(), req.getBuyerId(), req.getQuantity());

        return ResponseEntity.accepted().body(ApiSuccess.of(OrderResponse.from(createOrder), null));
    }

}
