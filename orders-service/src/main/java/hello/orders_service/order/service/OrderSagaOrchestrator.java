package hello.orders_service.order.service;

import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.exception.ApiException;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.client.ProductClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {
    private final OrderService orderService;
    private final ProductClient productClient;


    public Order startOrder(Long productId, String buyerId, int quantity) {
        // tx1 : Order PENDING 생성
        Order order = orderService.createOrderPending(productId, buyerId, quantity);

        // 멱등 키 생성
        String idemKey = "DEC-" + order.getId();

        try {
            // 상품서버 호출
            StockAdjustByOrderRequest request = new StockAdjustByOrderRequest(order.getId(), quantity);
            productClient.decreaseByOrder(productId, request, idemKey);

            //tx2 : 주문 확정
            return orderService.confirmOrder(order.getId());

        } catch (ProductClientException ex) {
            // tx2 : 주문 실패 처리
            // INSUFFICIENT_STOCK, PRODUCT_NOT_FOUND 등 클라이언트에서 익셉션이 온 경우
            // 주문을 실패로 기록, 상품 서버에서 재고 감소 실패로 다시 재고 증가 호출할 필요 없음.
            log.info("product client ex ", ex);
            return orderService.failOrder(order.getId(), ex.getMessage());

        } catch (DependencyFailedException ex) {
            // 상품 서버의 5xx 에러인 경우
            // 요청 재시도

        } catch (ApiException ex) {
            // 당장 생각나는 시나리오는 OrderNotFoundException 인 경우
            // 가능성은 낮아 보이지만 아무튼 발생할 경우 상품 서버에서는 재고가 감소되었고, 주문 서버에서 주문이 등록되지 않음.
            // 상품 서버 재고를 증가시켜야 한다.
            log.info("api ex ", ex);

        }  catch (RuntimeException ex) {
            // 런타임 익셉션 발생 시 주문은 생성되지 않음.
            // 상품 서버는 재고 감소를 수행했을 것.
            // 주문 서버와 상품 서버 일관성이 틀어짐 (주문은 없고 상품은 재고가 감소됨)
            // 다시 상품 재고를 올려야 함
            // 고민?? 런타임 익셉션을 통으로 잡는게 맞나 ?
        }

        return null;
    }
}
