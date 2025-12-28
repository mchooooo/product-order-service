package hello.orders_service.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockResultEvent {
    private Long orderId;      // 어떤 주문에 대한 결과인지
    private boolean success;   // 재고 차감 성공 여부
    private String message;    // 실패 시 사유 (예: "재고 부족", "상품 없음" 등)
}
