package hello.orders_service.order.client.dto;

import lombok.Data;

@Data
public class ProductDto {
    private Long id;
    private String name;
    private Integer price;
    private Integer stock;
    private String status;

}
