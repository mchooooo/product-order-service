package hello.orders_service.order.dto.response;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String buyerId;
    private Integer quantity;
    private OrderStatus status;
    private String failReason;
    private LocalDateTime createdAt;

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getBuyerId(), order.getQuantity(), order.getStatus(), order.getFailReason(), order.getCreateAt());
    }
}
