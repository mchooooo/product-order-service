package hello.product_service.product.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import hello.product_service.product.domain.Product;
import hello.product_service.product.domain.ProductStatus;
import hello.product_service.product.model.ProductSearchCondition;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

import static hello.product_service.product.domain.QProduct.*;

@Repository
public class ProductSearchRepository {
    private final JPAQueryFactory query;

    public ProductSearchRepository(EntityManager em) {
        this.query = new JPAQueryFactory(em);
    }

    public Page<Product> search(ProductSearchCondition cond, Pageable pageable) {
        String name = cond.getName();
        Integer price = cond.getPrice();
        Integer stock = cond.getStock();
        ProductStatus status = ProductStatus.ACTIVE;

        JPAQuery<Product> content = query.select(product)
            .from(product)
            .where(
                product.status.eq(status),
                likeProductName(name),
                maxPriceProduct(price),
                maxStockProduct(stock));

        if (pageable.isPaged()) {
            List<Product> products = content
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
            JPAQuery<Long> totalCountQuery = getTotalQuery(name, price, stock, status);
            return PageableExecutionUtils.getPage(products, pageable, () -> totalCountQuery.fetchOne());
        }

        return new PageImpl<>(content.fetch());

    }

    private JPAQuery<Long> getTotalQuery(String name, Integer price, Integer stock, ProductStatus status) {
        return query.select(product.count())
            .from(product)
            .where(
                product.status.eq(status),
                likeProductName(name),
                maxPriceProduct(price),
                maxStockProduct(stock));
    }

    private BooleanExpression likeProductName(String name) {
        if (StringUtils.hasText(name)) {
            return product.name.like("%" + name + "%");
        }

        return null;
    }

    private BooleanExpression maxPriceProduct(Integer price) {
        if (price != null) {
            return product.price.loe(price);
        }

        return null;
    }

    private BooleanExpression maxStockProduct(Integer stock) {
        if (stock != null) {
            return product.stock.loe(stock);
        }

        return null;
    }

}
