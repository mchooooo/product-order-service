package hello.orders_service.order.api.v1;

import hello.orders_service.common.web.response.ApiSuccess;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.dto.request.OrderCreateRequest;
import hello.orders_service.order.dto.response.OrderResponse;
import hello.orders_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class OrderV1Controller {
    private final OrderService orderService;

    @GetMapping("/v1/orders/{orderId}")
    public ResponseEntity<ApiSuccess<OrderResponse>> findById(@PathVariable("orderId") Long orderId) {
        log.info("주문 조회 요청, 주문 ID={}", orderId);
        Order findOrder = orderService.findById(orderId);
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(findOrder), null));
    }

    /**
     * V1 OrderService 사용
     * 상품 서버와 주문 서버의 일관성을 고려하지 않음.
     * 사가패턴 X
     */
    @PostMapping("/v1/orders")
    public ResponseEntity<ApiSuccess<OrderResponse>> create(@RequestBody OrderCreateRequest req) {
        Order createOrder = orderService.create(req.getProductId(), req.getBuyerId(), req.getQuantity());
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(createOrder), null));
    }

    /**
     * V1 OrderService 사용
     * 상품 서버와 주문 서버의 일관성을 고려하지 않음.
     * 사가패턴 X
     */
    @PostMapping("/v1/orders/{orderId}")
    public ResponseEntity<ApiSuccess<OrderResponse>> cancel(@PathVariable Long orderId) {
        Order cancelOrder = orderService.cancel(orderId);
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(cancelOrder), null));
    }
}

