package hello.orders_service.order.api;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.dto.request.OrderCreateRequest;
import hello.orders_service.order.dto.response.OrderResponse;
import hello.orders_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderApiController {
    private final OrderService orderService;


    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> findById(@PathVariable("orderId") Long orderId) {
        Order findOrder = orderService.findById(orderId);
        OrderResponse response = OrderResponse.from(findOrder);

        return ResponseEntity.ok(response);
    }
    // 2-1. 주문 생성
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> create(@RequestBody OrderCreateRequest req) {
        Order createOrder = orderService.create(req.getProductId(), req.getBuyerId() ,req.getQuantity());
        return ResponseEntity.ok(OrderResponse.from(createOrder)); // 최종 상태(PENDING→CONFIRMED/FAILED) 반영
    }

    // 2-2. 주문 취소
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long orderId) {
        Order cancelOrder = orderService.cancel(orderId);
        return ResponseEntity.ok(OrderResponse.from(cancelOrder));
    }

}
