package hello.orders_service.order.saga;


import hello.orders_service.order.domain.Order;
import lombok.Data;

@Data
public class OrderSagaContext {
    private Long productId;
    private String buyerId;
    private int quantity;

    private Long orderId;   // createOrderPending 후 채워짐
    private String decKey;  // DEC-{orderId}
    private String incKey;  // INC-{orderId}

    private Order order;
}
