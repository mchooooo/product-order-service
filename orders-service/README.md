## 기능 목록
### order-service
- 주문 생성
- 주문 취소
- 주문 조회

---

## 엔티티 설계
- **Order**
    - `id (PK)`, `productId`, `buyerId(optional)`, `quantity`, `status[PENDING|CONFIRMED|CANCELLED|FAILED]`, `failReason (nullable)`, `createdAt`, `updatedAt`
 
---

## API 명세
### 2-1. 주문 생성

- **POST** `/orders`
- **Body** `{ "productId": 1, "quantity": 3, "buyerId": "u-001" }`
- **동작**:
  -   `PENDING` 저장 →
  - product-service의 `/stock/decrease-by-order` 호출(헤더 `Idempotency-Key: DEC-{orderId}`) →
  - 성공이면 `CONFIRMED`, 실패면 `FAILED`
- **200** 주문 JSON (최종 상태 반영)
- **실패**: 상품 서버 4xx/5xx/타임아웃 시 `FAILED`로 갱신(+ `failReason`)

### 2-2. 주문 취소

- **POST** `/orders/{orderId}/cancel`
- **규칙**: `CONFIRMED` 상태에서만 가능
- **동작**: product-service `/stock/increase-by-order` 호출(헤더 `Idempotency-Key: INC-{orderId}`) 성공 시 `CANCELLED`로 변경
- **200** 취소된 주문 JSON / **409** 불가 상태(예: 이미 CANCELLED/FAILED/PENDING)

### 2-3. 주문 조회

- **GET** `/orders/{orderId}` → 200 주문 JSON
- **GET** `/orders?buyerId=u-001&page=0&size=20&status=CONFIRMED` → 200 페이징 목록 JSON
