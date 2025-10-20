package hello.product_service.product.model;

import hello.product_service.product.domain.Product;
import hello.product_service.product.domain.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString
public class ProductDto {
    private String name;
    private Integer price;
    private Integer stock;
    private ProductStatus productStatus;


    public static Product fromDto(ProductDto dto) {
        return new Product(dto.getName(), dto.getPrice(), dto.getStock(), dto.getProductStatus());
    }

    public static ProductDto of(Product product) {
        return new ProductDto(product.getName(), product.getPrice(), product.getStock(), product.getStatus());
    }
}
