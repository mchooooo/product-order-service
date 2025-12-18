package hello.product_service.product.repository;

import hello.product_service.product.domain.Product;
import hello.product_service.product.domain.StockStrategy;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id=:productId AND p.stock >= :qty")
    int decrement(@Param("productId") Long productId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id=:productId")
    int increment(@Param("productId") Long productId, @Param("qty") int qty);

    // 행 잠금 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")) // ms
    @Query("select p from Product p where p.id = :id")
    Product findForUpdateWithTimeout(@Param("id") Long id);

    @Query("select p.stockStrategy from Product p where p.id = :productId")
    StockStrategy findStockStrategyById(@Param("productId") Long productId); // 재고 전략 조회

    @Query("select p.stock from Product p where p.id = :productId")
    int findStockById(@Param("productId") Long productId);
}
