package hello.product_service.product.model;

import lombok.Data;

@Data
public class OrderRequest {
    private Long orderId;
    private int quantity;
}
