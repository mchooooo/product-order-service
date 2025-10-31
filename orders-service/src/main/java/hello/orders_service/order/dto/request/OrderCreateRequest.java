package hello.orders_service.order.dto.request;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class OrderCreateRequest {
    private Long productId;
    private Integer quantity;
    private String buyerId;
}
