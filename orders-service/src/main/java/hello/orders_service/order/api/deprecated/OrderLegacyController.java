package hello.orders_service.order.api.deprecated;

import hello.orders_service.common.web.response.ApiSuccess;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.dto.response.OrderResponse;
import hello.orders_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레거시 경로 유지용 컨트롤러.
 * 신규 클라이언트는 {@code /v1/orders/{orderId}} 경로를 사용하세요.
 */
@Deprecated
@RestController
@RequiredArgsConstructor
@Slf4j
public class OrderLegacyController {
    private final OrderService orderService;

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiSuccess<OrderResponse>> findById(@PathVariable("orderId") Long orderId) {
        log.info("[DEPRECATED] 주문 조회 요청, 주문 ID={}", orderId);
        Order findOrder = orderService.findById(orderId);
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(findOrder), null));
    }
}

