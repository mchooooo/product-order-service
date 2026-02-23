package hello.product_service.product;

import hello.product_service.product.domain.Member;
import hello.product_service.product.domain.Product;
import hello.product_service.product.domain.ProductStatus;
import hello.product_service.product.domain.StockStrategy;
import hello.product_service.product.repository.MemberRepository;
import hello.product_service.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductDataInit {
    private final ProductRepository productRepository;
//    private final MemberRepository memberRepository;
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        Product product = new Product("book", 10000, 100, ProductStatus.ACTIVE, StockStrategy.REDIS_FIRST);
        Product product2 = new Product("movie", 15000, 10, ProductStatus.ACTIVE, StockStrategy.DB_ONLY);
        productRepository.save(product);
        productRepository.save(product2);

//        for (int i = 0; i < 100; i++) {
//            Member member = Member.create("name" + i, i);
//            memberRepository.save(member);
//        }

    }

}
