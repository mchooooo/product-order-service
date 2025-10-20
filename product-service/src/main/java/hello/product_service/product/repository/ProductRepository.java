package hello.product_service.product.repository;

import hello.product_service.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id=:productId AND p.stock >= :qty")
    int decrement(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id=:productId")
    int increment(@Param("productId") Long productId, @Param("qty") int qty);
}
