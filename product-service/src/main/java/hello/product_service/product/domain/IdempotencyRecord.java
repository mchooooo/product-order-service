package hello.product_service.product.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class IdempotencyRecord {
    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true)
    private String requestId;

    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;

    private String message;

    private Integer remainingStock;

    @CreationTimestamp
    private LocalDateTime createAt;

    //== 생성 메서드 ==//
    public static IdempotencyRecord create(String requestId, IdempotencyStatus status, String message, Integer remainingStock) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.requestId = requestId;
        record.status = status;
        record.message = message;
        record.remainingStock = remainingStock;
        return record;
    }


}
