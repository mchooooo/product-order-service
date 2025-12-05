package hello.orders_service.order.saga;

import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.client.dto.StockResult;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.exception.ApiException;
import hello.orders_service.order.exception.DependencyFailedException;
import hello.orders_service.order.exception.ErrorCode;
import hello.orders_service.order.exception.OrderStateException;
import hello.orders_service.order.exception.client.ProductClientException;
import hello.orders_service.order.saga.step.CancelOrderStep;
import hello.orders_service.order.saga.step.CreateOrderPendingStep;
import hello.orders_service.order.saga.step.DecreaseStockAndConfirmStep;
import hello.orders_service.order.saga.step.IncreaseStockByCancelStep;
import hello.orders_service.order.service.OrderService;
import hello.orders_service.saga.SagaErrorType;
import hello.orders_service.saga.SagaException;
import hello.orders_service.saga.SagaStep;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {
    private final OrderService orderService;
    private final ProductClient productClient;

    // Step 빈들 주입
    private final CreateOrderPendingStep createOrderPendingStep;
    private final DecreaseStockAndConfirmStep decreaseStockAndConfirmStep;
    private final IncreaseStockByCancelStep increaseStockByCancelStep;
    private final CancelOrderStep cancelOrderStep;

    // 플로우별 step 리스트
    private List<SagaStep<OrderSagaContext>> orderSteps;
    private List<SagaStep<OrderSagaContext>> cancelSteps;

    @PostConstruct
    void init() {
        // 주문 생성 플로우: PENDING → 재고 차감 & CONFIRM
        this.orderSteps = List.of(
            createOrderPendingStep,
            decreaseStockAndConfirmStep
        );

        // 주문 취소 플로우: 재고 복원(INC) → CANCELLED
        this.cancelSteps = List.of(
            increaseStockByCancelStep,
            cancelOrderStep
        );
    }


    /**
     * ============= Saga 패턴 도입 버전 2 ================
     * 주문, 주문 취소 플로우를 SagaStep 구현체를 통해 수행
     * 새로운 플로우 (배송, 결제)가 생길 경우 쉽게 확장 가능
     */

    /** 주문 생성 사가: PENDING → (재고 차감 DEC-{orderId}) → CONFIRMED/FAILED */
    public Order startOrder(Long productId, String buyerId, int quantity) {
        OrderSagaContext context = new OrderSagaContext();
        context.setProductId(productId);
        context.setBuyerId(buyerId);
        context.setQuantity(quantity);

        return runSaga(orderSteps, context);
    }

    /** 주문 취소 사가: CONFIRMED → (재고 복원 INC-{orderId}) → CANCELLED */
    public Order startCancel(Long orderId) {
        // 기존 startCancel 의 상태 검증 로직
        Order order = orderService.findById(orderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.info("order status = {}", order.getStatus());
            throw new OrderStateException(ErrorCode.INVALID_STATE.getMessage(), null);
        }

        OrderSagaContext context = new OrderSagaContext();
        context.setOrderId(orderId);
        context.setProductId(order.getProductId());
        context.setQuantity(order.getQuantity());
        context.setIncKey("INC-" + orderId);
        context.setDecKey("DEC-" + orderId);
        context.setOrder(order); // 기본은 CONFIRMED

        return runSaga(cancelSteps, context);
    }

    // 주문 / 취소 공통 실행 로직
    Order runSaga(List<SagaStep<OrderSagaContext>> steps, OrderSagaContext ctx) {
        int completed = 0;

        try {
            for (SagaStep<OrderSagaContext> step : steps) {
                log.info("Executing step: {}", step.getClass().getSimpleName());
                step.execute(ctx);
                completed++;
            }
            return ctx.getOrder();

        } catch (SagaException ex) {
            SagaErrorType type = ex.getType();
            log.warn("SagaException occurred. type={}, msg={}", type, ex.getMessage());
            switch (type) {
                case BUSINESS -> {
                    log.warn("Saga business failure. ctx={}", ctx);
                    return ctx.getOrder();
                }

                case RETRYABLE -> {
                    // 재시도 대상: 지금은 그대로 던지고, 나중에 재시도 메커니즘 붙일 수 있음
                    log.error("Saga retryable failure. ctx={}", ctx, ex);
                    throw ex;
                }

                case COMPENSATE -> {
                    log.error("Saga compensatable failure. starting compensation... ctx={}", ctx, ex);
                    compensate(steps, ctx, completed - 1);
                    return ctx.getOrder();
                }

                default -> {
                    // SagaErrorType ENUM 추가 시 아래 추가
                    log.error("Unknown SagaErrorType, treat as COMPENSATABLE. ctx={}", ctx, ex);
                    compensate(steps, ctx, completed - 1);
                    return ctx.getOrder();
                }
            }
        } catch (RuntimeException ex) {
            // SagaException 으로 래핑되지 않은 완전 예상 밖 예외도 안전하게 보상 대상으로 본다.
            log.error("Saga unknown failure. starting compensation... ctx={}", ctx, ex);
            compensate(steps, ctx, completed - 1);
            return ctx.getOrder();
        }
    }

    void compensate(List<SagaStep<OrderSagaContext>> steps, OrderSagaContext ctx, int lastIndex) {
        for (int i = lastIndex; i >= 0; i--) {
            SagaStep<OrderSagaContext> step = steps.get(i);
            try {
                log.info("Compensating step: {}", step.getClass().getSimpleName());
                step.compensate(ctx);
            } catch (Exception ex) {
                log.error("Saga compensation step[{}] failed. step={}, ctx={}",
                    i, step.getClass().getSimpleName(), ctx, ex);
            }
        }
    }

    /**
     * ============= Saga 패턴 도입 버전 1 ================
     * 문제점 :
     * - 주문 생성 / 취소 흐름과 외부 서비스 호출 로직, 예외 분기(4xx/5xx/로컬 예외)가
     *      한 메서드 안의 긴 try-catch 블록으로 뒤섞여 있어 가독성이 떨어진다.
     * - 새 단계(예: 결제 서비스 추가)나 새로운 사가 플로우를 추가할 때 수정 범위가 크다.

     * 개선 방향 :
     * - 주문 생성 / 취소 플로우를 SagaStep 구현체들로 분리하여
     *      각 단계를 작은 단위로 만든다.
     * - 이후 단계 추가(결제, 배송 등) 시 확장성을 높인다.
     */
    public Order startOrderV1(Long productId, String buyerId, int quantity) {
        // tx1 : Order PENDING 생성
        Order order = orderService.createOrderPending(productId, buyerId, quantity);

        // 멱등 키 생성
        String idemKey = "DEC-" + order.getId();
        log.info("idempotency key = {}", idemKey);

        try {//tx2 : 주문 확정
            // 상품서버 호출
            StockAdjustByOrderRequest request = new StockAdjustByOrderRequest(order.getId(), quantity);
            StockResult stockResult = productClient.decreaseByOrder(productId, request, idemKey).getData();

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
            compensateIncreaseV1(productId, order.getId(), quantity);        // 보상(INC-{orderId})

            //tx2 : 주문 실패 처리
            return orderService.failOrder(order.getId(), "COMPENSATED");

        }  catch (RuntimeException ex) {// --- 알수없는 예외: 안전하게 원복 + FAILED(COMPENSATED) ---
            // 런타임 익셉션 발생 시 주문은 생성되지 않음.
            // 상품 서버는 재고 감소를 수행했을 것.
            // 주문 서버와 상품 서버 일관성이 틀어짐 (주문은 없고 상품은 재고가 감소됨)
            // 다시 상품 재고를 올려야 함
            log.error("Local error after DEC call. compensating... orderId={}", order.getId(), ex);
            compensateIncreaseV1(productId, order.getId(), quantity);        // 보상(INC-{orderId})
            return orderService.failOrder(order.getId(), "COMPENSATED");
        }

    }

    /** 주문 취소 사가: CONFIRMED → (재고 복원: INC-{orderId}) → CANCELLED */
    public Order startCancelV1(Long orderId) {
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
            compensateDecreaseV1(productId, order.getId(), qty);
            return order; // 상태 유지

        } catch (RuntimeException ex) {
            // 알 수 없는 오류: 상품 서버 재고 감소 성공
            log.error("Cancel local error. orderId={}", orderId, ex);
            compensateDecreaseV1(productId, order.getId(), qty);
            return order; // 상태 유지
        }
    }


    /** 보상 트랜잭션: 재고 증가 (멱등키 INC-{orderId}) */
    private void compensateIncreaseV1(Long productId, Long orderId, int qty) {
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
    private void compensateDecreaseV1(Long productId, Long orderId, int qty) {
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
