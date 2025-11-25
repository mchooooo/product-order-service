package hello.orders_service.order.service;

import feign.FeignException;
import hello.orders_service.common.ApiSuccess;
import hello.orders_service.order.client.ProductClient;
import hello.orders_service.order.client.dto.StockAdjustByOrderRequest;
import hello.orders_service.order.client.dto.StockResult;
import hello.orders_service.order.domain.Order;
import hello.orders_service.order.domain.OrderStatus;
import hello.orders_service.order.exception.*;
import hello.orders_service.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrders(String buyerId, OrderStatus status, Pageable pageable) {
        if (buyerId != null && status != null) {
            return orderRepository.findByBuyerIdAndStatus(buyerId, status, pageable);
        } else if (buyerId != null) {
            return orderRepository.findByBuyerId(buyerId, pageable);
        }
        return orderRepository.findAll(pageable);
    }

    /**
     * orderV1
     * 하나의 트랜잭션에서 서버 호출 및 주문 생성 모두 관리
     * 상품 서버와 주문 서버의 일관성을 맞추기 어려울 수 있음 -> 재고 감소 호출 후 알 수 없는 익셉션 발생 시 주문은 생성되지 않음.
     * orderV2 (사가패턴?) 도입
     */
    @Transactional
    public Order create(Long productId, String buyerId, int quantity) {
        // 1. 주문 엔티티 생성
        Order createOrder = Order.create(productId, buyerId, quantity);
        orderRepository.save(createOrder);

        // 2. product server 호출
        String idempotencyKey = "DEC-" + createOrder.getId();
        StockAdjustByOrderRequest request = new StockAdjustByOrderRequest(createOrder.getId(), quantity);

        // 3. 익셉션 처리, 주문 서버 익셉션으로 추상화
        try {
            ApiSuccess<StockResult> result = productClient.decreaseByOrder(productId, request, idempotencyKey);
            StockResult stockResult = result.getData();
            log.info("stockResult = {}", stockResult);
            if (!stockResult.isSuccess()) {
                createOrder.failStatus(stockResult.getMessage() != null ? stockResult.getMessage() : "STOCK_DECREASE_FAIL");
            } else {
                createOrder.confirmStatus();
            }

        } catch (ApiException e) {
            createOrder.failStatus(e.getMessage());
            throw new DependencyFailedException(e.getCode(), e.getMessage(), e.getDetails(), e);
        } catch (FeignException e) {
            createOrder.failStatus("상품 서버 오류");
            throw new DependencyFailedException(
                ErrorCode.DEPENDENCY_FAILED,
                "상품 서버 호출 실패",
                Map.of("status", e.status(), "body", e.contentUTF8()),
                e
            );
        }

        return createOrder;

    }


    @Transactional
    public Order cancel(Long orderId) {
        // 1. 취소할 주문 찾기
        Order findOrder = orderRepository.findById(orderId).orElseThrow();

        // 2. CONFIRM 상태에서만 취소 가능
        if (findOrder.getStatus() != OrderStatus.CONFIRMED) {
            throw new OrderStateException("현재 상태(" + findOrder.getStatus() + ")에서는 취소할 수 없습니다.", findOrder.getStatus().name());
        }

        // 3. 상품 서버 호출
        String idempotencyKey = "INC-" + findOrder.getId();
        StockAdjustByOrderRequest request = StockAdjustByOrderRequest.create(findOrder.getId(), findOrder.getQuantity());

        try {
            productClient.increaseByOrder(findOrder.getProductId(), request, idempotencyKey);
            findOrder.cancelStatus();
            return findOrder;
        } catch (FeignException e) {
            // 취소 실패 시 주문 상태 유지(CONFIRMED) — 스펙 규칙
            throw new DependencyFailedException(ErrorCode.DEPENDENCY_FAILED, "상품 재고 복원 실패", null, e);
        }

    }

    /**
     * orderV2 상품 서버와 주문 서버의 일관성을 맞추기 위해 OrderSagaOrchestrator 도입
     */
    @Transactional
    public Order createOrderPending(Long productId, String buyerId, int quantity) {
        Order o = Order.create(productId, buyerId, quantity);
        orderRepository.save(o);
        return o;
    }

    /**
     * orderV2 상품 서버와 주문 서버의 일관성을 맞추기 위해 OrderSagaOrchestrator 도입
     */
    @Transactional
    public Order confirmOrder(Long orderId) {
        Order findOrder = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(ErrorCode.ORDER_NOT_FOUND, ErrorCode.ORDER_NOT_FOUND.getMessage(), null, null));
        findOrder.confirmStatus();
        return findOrder;
    }

    /**
     * orderV2 상품 서버와 주문 서버의 일관성을 맞추기 위해 OrderSagaOrchestrator 도입
     */
    @Transactional
    public Order failOrder(Long orderId, String message) {
        Order findOrder = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(ErrorCode.ORDER_NOT_FOUND, ErrorCode.ORDER_NOT_FOUND.getMessage(), null, null));
        findOrder.failStatus(message);
        return findOrder;
    }

    /**
     * orderV2 상품 서버와 주문 서버의 일관성을 맞추기 위해 OrderSagaOrchestrator 도입
     */
    @Transactional
    public Order cancelOrder(Long orderId) {
        Order findOrder = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(ErrorCode.ORDER_NOT_FOUND, ErrorCode.ORDER_NOT_FOUND.getMessage(), null, null));
        findOrder.cancelStatus();
        return findOrder;
    }

}
