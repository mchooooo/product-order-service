package hello.orders_service.order.api.internal;

import hello.orders_service.common.web.response.ApiSuccess;
import hello.orders_service.order.dto.response.OrderResponse;
import hello.orders_service.order.dto.response.OutboxResponse;
import hello.orders_service.order.service.OrderRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderAdminController {
    private final OrderRecoveryService orderRecoveryService;

    @PostMapping("/internal/orders/{orderId}/retry")
    public ResponseEntity<ApiSuccess<OrderResponse>> retryFailedOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiSuccess.of(OrderResponse.from(orderRecoveryService.retryFailedOrder(orderId)), null));
    }

    @PostMapping("/internal/outbox/{outboxId}/requeue")
    public ResponseEntity<ApiSuccess<OutboxResponse>> requeueOutbox(@PathVariable Long outboxId) {
        return ResponseEntity.ok(ApiSuccess.of(OutboxResponse.from(orderRecoveryService.requeueOutbox(outboxId)), null));
    }
}
