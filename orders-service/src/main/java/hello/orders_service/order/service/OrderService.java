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

    @Transactional
    public Order create(Long productId, String buyerId, int quantity) {
        //todo : 프로덕트 서비스의 실패 시 오더 서비스와 프로덕트 서비스가 일관된 상태를 유지할 수 있도록 하는 방법 고민
        //todo : ex) 해당 로직 실행 중 익셉션이 발생할 경우 프로덕트 서비스에는 재고가 차감되고, 오더 서비스는 익셉션으로 인해 롤백이 되면 일관성이 유지되지 않음
        //todo : 힌트 -> 사가 패턴 도입, 서비스에 엮여있는 트랜잭션을 관리하고, 실패 시 보상 설계

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


    @Transactional
    public Order createOrderPending(Long productId, String buyerId, int quantity) {
        Order o = Order.create(productId, buyerId, quantity);
        orderRepository.save(o);
        return o;
    }

    @Transactional
    public Order confirmOrder(Long orderId) {
        Order findOrder = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(ErrorCode.ORDER_NOT_FOUND, ErrorCode.ORDER_NOT_FOUND.getMessage(), null, null));

        findOrder.confirmStatus();

        return findOrder;
    }

    @Transactional
    public Order failOrder(Long orderId, String message) {
        Order findOrder = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(ErrorCode.ORDER_NOT_FOUND, ErrorCode.ORDER_NOT_FOUND.getMessage(), null, null));

        findOrder.failStatus(message);
        return findOrder;
    }

}
