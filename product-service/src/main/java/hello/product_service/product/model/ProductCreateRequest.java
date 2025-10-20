package hello.product_service.product.model;

import hello.product_service.product.domain.Product;
import hello.product_service.product.domain.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProductCreateRequest {
    private String name;
    private int price;
    private int stock;


    public static Product fromDto(ProductCreateRequest dto) {
        return new Product(dto.getName(), dto.getPrice(), dto.getStock(), ProductStatus.ACTIVE);
    }
}
