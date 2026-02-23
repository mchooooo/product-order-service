package hello.product_service.product.api;

import hello.product_service.product.domain.Member;
import hello.product_service.product.repository.MemberRepository;
import hello.product_service.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ProductAdminController {
    private final ProductService productService;
    private final MemberRepository memberRepository;

    /**
     * 관리자가 특정 상품의 재고를 Redis에 로드하는 API
     * (이벤트 시작 직전에 사용)
     */
    @PostMapping("/admin/products/{productId}/load-stock-redis")
    public String loadStockRedis(@PathVariable("productId") Long productId) {
        try {
            int stock = productService.loadInitStockRedis(productId);
            return String.format("상품 ID %d의 재고 %d개를 Redis에 성공적으로 로드했습니다.", productId, stock);
        } catch (Exception e) {
            return "Redis 로드 실패: " + e.getMessage();
        }
    }

    @GetMapping("/members")
    public List<Member> members() {
        List<Member> members = memberRepository.findAll();
        log.info("[init data] {}", members.size());

        for (int i = 0; i < members.size(); i++) {
            Member find = members.get(i);
            log.info("[find] id={}, name={}, age={}", find.getId(), find.getName(), find.getAge());
            if (find.getAge() < 10) {
                log.info("[result] id={}, name={}, age={}", find.getId(), find.getName(), find.getAge());
                members.remove(i);
            }
        }


        System.out.println(members.size());

        return members;
    }

}
