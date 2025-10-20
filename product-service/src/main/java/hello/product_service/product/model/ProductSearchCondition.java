package hello.product_service.product.model;

import lombok.Data;

@Data
public class ProductSearchCondition {
    private String name;
    private Integer price;
    private Integer stock;
}
