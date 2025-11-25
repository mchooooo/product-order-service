package hello.orders_service.order.service;

import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.exception.ApiException;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.ErrorCode;
import hello.orders_service.order.exception.OrderStateException;
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
        log.info("idempotency key = {}", idemKey);

        try {//tx2 : 주문 확정
            // 상품서버 호출
            StockAdjustByOrderRequest request = new StockAdjustByOrderRequest(order.getId(), quantity);
            productClient.decreaseByOrder(productId, request, idemKey);

            // 보상 호출 테스트
            if ("apiEx".equals(buyerId)) {
                throw new ApiException(null, "test call");
            }

            return orderService.confirmOrder(order.getId());
        } catch (ProductClientException ex) { // --- 업무 4xx: 보상 불필요(차감 안 됨 가정) ---
            // INSUFFICIENT_STOCK, PRODUCT_NOT_FOUND 등 클라이언트에서 익셉션이 온 경우
            // 주문을 실패로 기록, 상품 서버에서 재고 감소 실패로 다시 재고 증가 호출할 필요 없음.
            // tx2 : 주문 실패 처리
            log.warn("error from product. orderId={}, msg={}", order.getId(), ex.getMessage());
            return orderService.failOrder(order.getId(), ex.getMessage());

        } catch (DependencyFailedException ex) { // --- 5xx/타임아웃: 재시도 대상 ---
            log.error("Dependency failure (retry). orderId={}, upstreamStatus={}", order.getId(), ex.getCode().getStatus(), ex);
            // 상품 서버의 5xx 에러인 경우
            // 요청 재시도
            // 아직 구현 X
            throw ex;

        } catch (ApiException ex) {// --- 로컬 예외: 안전하게 원복 + FAILED(COMPENSATED) ---
            // 당장 생각나는 시나리오는 OrderNotFoundException 인 경우
            // 가능성은 낮아 보이지만 아무튼 발생할 경우 상품 서버에서는 재고가 감소되었고, 주문 서버에서 주문이 등록되지 않음.
            // 상품 서버 재고를 증가시켜야 한다.
            log.error("Local error after DEC call. compensating... orderId={}", order.getId(), ex);
            compensateIncrease(productId, order.getId(), quantity);        // 보상(INC-{orderId})

            //tx2 : 주문 실패 처리
            return orderService.failOrder(order.getId(), "COMPENSATED");

        }  catch (RuntimeException ex) {// --- 알수없는 예외: 안전하게 원복 + FAILED(COMPENSATED) ---
            // 런타임 익셉션 발생 시 주문은 생성되지 않음.
            // 상품 서버는 재고 감소를 수행했을 것.
            // 주문 서버와 상품 서버 일관성이 틀어짐 (주문은 없고 상품은 재고가 감소됨)
            // 다시 상품 재고를 올려야 함
            log.error("Local error after DEC call. compensating... orderId={}", order.getId(), ex);
            compensateIncrease(productId, order.getId(), quantity);        // 보상(INC-{orderId})
            return orderService.failOrder(order.getId(), "COMPENSATED");
        }

    }

    /** 주문 취소 사가: CONFIRMED → (재고 복원: INC-{orderId}) → CANCELLED */
    public Order startCancel(Long orderId) {
        // 0) 상태 검증: CONFIRMED에서만 취소 가능
        Order order = orderService.findById(orderId);

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.info("order status = {}", order.getStatus());
            throw new OrderStateException(ErrorCode.INVALID_STATE.getMessage(), null);
        }

        Long productId = order.getProductId();
        int qty = order.getQuantity();
        String incKey = "INC-" + orderId;

        try {
            // 1) 보상 트랜잭션(재고 복원) 호출 — 멱등 키 사용
            StockAdjustByOrderRequest incReq = new StockAdjustByOrderRequest(orderId, qty);
            productClient.increaseByOrder(productId, incReq, incKey);

            // 2) TX: 주문을 CANCELLED 확정
            return orderService.cancelOrder(order.getId());

        } catch (DependencyFailedException ex) {
            // 재시도 대상: 상품 서버 5xx/타임아웃
            // 취소 실패 시 규칙상 주문은 CONFIRMED 유지
            log.error("Cancel dependency failure (retry target). orderId={}", orderId, ex);

            // 아직 재시도 구현 x
            throw ex;

        } catch (ProductClientException ex) {
            // 4xx 비즈니스 실패(예: PRODUCT_NOT_FOUND): 취소 불가 → CONFIRMED 유지
            log.warn("Cancel business failure. orderId={}, msg={}", orderId, ex.getMessage());
            return order; // 상태 그대로 유지

        } catch (ApiException ex) {
            // 로컬 오류: 상품 서버 재고 감소 성공
            // 상품 서버는 재고가 증가 됐으나 주문이 취소 처리가 안됨.
            log.error("Cancel local error. orderId={}", orderId, ex);
            compensateDecrease(productId, order.getId(), qty);
            return order; // 상태 유지

        } catch (RuntimeException ex) {
            // 알 수 없는 오류: 상품 서버 재고 감소 성공
            log.error("Cancel local error. orderId={}", orderId, ex);
            compensateDecrease(productId, order.getId(), qty);
            return order; // 상태 유지
        }
    }


    /** 보상 트랜잭션: 재고 증가 (멱등키 INC-{orderId}) */
    private void compensateIncrease(Long productId, Long orderId, int qty) {
        try {
            String incKey = "INC-" + orderId;
            StockAdjustByOrderRequest incReq = new StockAdjustByOrderRequest(orderId, qty);
            productClient.increaseByOrder(productId, incReq, incKey);
        } catch (Exception ex) {
            // 보상 실패는 강한 로깅
            log.error("Compensation failed. orderId={}", orderId, ex);
        }
    }

    /** 보상 트랜잭션: 재고 감소 (멱등키 DEC-{orderId}) */
    private void compensateDecrease(Long productId, Long orderId, int qty) {
        try {
            String decKey = "DEC-" + orderId;
            StockAdjustByOrderRequest decReq = new StockAdjustByOrderRequest(orderId, qty);
            productClient.decreaseByOrder(productId, decReq, decKey);
        } catch (Exception ex) {
            // 보상 실패는 강한 로깅
            log.error("Compensation failed. orderId={}", orderId, ex);
        }
    }
}
