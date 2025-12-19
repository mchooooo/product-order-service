package hello.product_service.product.domain;

import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.model.ProductDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@ToString
public class Product {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private Integer price;
    private Integer stock;

    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    @Enumerated(EnumType.STRING)
    private StockStrategy stockStrategy;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updateAt;

    public Product(String name, int price, int stock, ProductStatus status) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.status = status;
    }

    public Product(Long id, String name, int price, int stock, ProductStatus status, StockStrategy stockStrategy) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.status = status;
        this.stockStrategy = stockStrategy;
    }

    public Product(String name, int price, int stock, ProductStatus status, StockStrategy stockStrategy) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.status = status;
        this.stockStrategy = stockStrategy;
    }

    public Product(Long id, String name, int price, int stock, ProductStatus status) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.status = status;
    }

    // == 비즈니스 메서드 == //
    public void update(ProductDto productDto) {
        if (StringUtils.hasText(productDto.getName())) {
            this.name = productDto.getName();
        }

        if (productDto.getPrice() != null) {
            this.price = productDto.getPrice();
        }

        if (productDto.getStock() != null) {
            this.stock = productDto.getStock();
        }

        if (productDto.getProductStatus() != null) {
            this.status = productDto.getProductStatus();
        }

    }

    public void delete() {
        this.status = ProductStatus.DELETE;
    }

    public void decreaseStock(int qty) {
        if (this.stock - qty >= 0) {
            this.stock -= qty;
        } else {
            throw new InsufficientStockException(this.id, this.stock);
        }
    }

    public void updateStockStrategy(StockStrategy stockStrategy) {
        this.stockStrategy = stockStrategy;
    }
}
