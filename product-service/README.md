## 기능 목록

### product-service

- 상품 CRUD
- 주문에 의한 재고 증가
- 주문에 의한 재고 감소

---

## 엔티티 설계

- **Product**
    - `id (PK)`, `name`, `price`, `stock`, `status[ACTIVE|DISCONTINUED]`, `createdAt`, `updatedAt`
- **StockLedger** (재고 원장)
    - `id (PK)`, `productId (FK)`, `direction[IN|OUT]`, `quantity`, `reason[ORDER_DECREMENT|ORDER_INCREMENT|MANUAL|ADJUST]`, `orderId (nullable)`, `requestId`, `createdAt`
- **IdempotencyRecord**
    - `id (PK)`, `requestId (UNIQUE)`, `success(Boolean)`, `message`, `payloadHash(optional)`, `createdAt`
 
---

## API 명세
### 1. 상품 등록

- **POST** `/products`
- **Body** `{ "name": "Keyboard", "price": 39000, "stock": 100 }`
- **200** `{ "id": 1, "name": ..., "price": ..., "stock": ..., "status": "ACTIVE" }`

### 2. 상품 수정

- **PATCH** `/products/{productId}`
- **Body** `{ "name": "Gaming Keyboard", "price": 42000, "status": "ACTIVE" }` *(부분 수정)*
- **200** 상품 전체 JSON

### 3. 상품 조회(단건/목록)

- **GET** `/products/{productId}` → 200 상품 JSON
- **GET** `/products?query=key&page=0&size=20&status=ACTIVE` → 200 페이징 목록 JSON

### 4. 상품 삭제

- **DELETE** `/products/{productId}`
- 규칙: 기본은 **논리삭제** 권장(예: `status=DISCONTINUED`).
- **204** (성공)

### 5. 주문에 의한 감소 (재고 차감)

- **PATCH** `/products/{productId}/stock/decrease-by-order`
- **Headers**: `Idempotency-Key: DEC-{orderId}`
- **Body** `{ "orderId": 123, "quantity": 3, "requestId": "DEC-123" }`
- **200** 성공 `{ "success": true, "remainingStock": 97, "message": "OK" }`
- **409/422** 실패 `{ "success": false, "message": "INSUFFICIENT_STOCK" }`
- **원자성**: `UPDATE ... SET stock = stock - :qty WHERE id=:id AND stock >= :qty`
- **멱등성**: `requestId`/`Idempotency-Key` 기반으로 **중복 차감 방지**, 이전 결과 반환
- **원장 기록**: StockLedger에 `OUT, reason=ORDER_DECREMENT`

### 6. 주문에 의한 추가 (주문 취소/실패 보상)

- **PATCH** `/products/{productId}/stock/increase-by-order`
- **Headers**: `Idempotency-Key: INC-{orderId}`
- **Body** `{ "orderId": 123, "quantity": 3, "reason": "ORDER_CANCELLED", "requestId": "INC-123" }`
- **200** `{ "success": true, "remainingStock": 100, "message": "OK" }`
- **멱등성**: 동일 `requestId` 재요청 시 결과 재사용
- **원장 기록**: StockLedger에 `IN, reason=ORDER_INCREMENT` (orderId 포함)
