package hello.orders_service.order.dto.request;

import lombok.Data;

@Data
public class OrderCreateRequest {
    private Long productId;
    private Integer quantity;
    private String buyerId;
}
