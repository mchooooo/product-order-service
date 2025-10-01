# product-order-service
# 프로젝트 개요

- **목표**: 주문 서버(order-service)와 상품 서버(product-service)를 분리(MSA 입문)하여, 주문 생성/취소/조회와 상품 CRUD 및 주문에 따른 재고 증감 흐름을 구현한다.
- **통신**: 동기 REST api (필수), 이후 단계에서 Kafka 등 비동기 확장 가능(선택).
- **DB**: 서비스별 H2 DB(로컬) — 운영 단계에서 MySQL/Postgres 전환 권장.
- **핵심 원칙**: 서비스 경계 분리, 데이터베이스 분리, 멱등성(Idempotency), 원자적 재고 갱신, 실패/재시도 내결함성.

---
## 엔티티 설계, API 명세
### product-service
  [product-service README.md]()
### order-service
  [order-service README.md]()
  
---
## 필수 기능 요구사항

### product-service

- 상품 CRUD
- 주문에 의한 재고 증가
- 주문에 의한 재고 감소

### order-service

- 주문 생성
- 주문 취소
- 주문 조회

---

## 필수 비기능 요구사항

- **멱등성**: 상품 서버가 `Idempotency-Key/requestId` 저장 → 동일 키 재요청 시 이전 결과 반환
- **원자적 업데이트**: `UPDATE ... SET stock = stock - :qty WHERE id=:id AND stock >= :qty`
- **타임아웃/재시도**: 주문→상품 호출에 클라이언트 타임아웃, 재시도(최대 1~2회) 적용
- **에러 매핑**: 5xx/타임아웃 → 주문 `FAILED("PRODUCT_UNAVAILABLE" 등)`
- **로깅**: 주문ID, requestId, 응답코드, 소요시간

---

## 핵심 흐름 (시퀀스)

1. `POST /orders` 수신 → 주문 `PENDING` 저장
2. `PATCH /products/{id}/stock` 호출(멱등키=ORDER-{orderId})
3. 응답이 `success=true` → 주문 `CONFIRMED`
4. `success=false` 또는 오류 → 주문 `FAILED` + 사유 기록

---

## 상태 전이 규칙

```smalltalk
PENDING --(decrease 성공)--> CONFIRMED --(취소)--> CANCELLED
│
└--(decrease 실패/에러)--> FAILED
```

- 취소는 `CONFIRMED`에서만 허용. 취소 실패 시 주문은 `CONFIRMED` 유지

---

## 에러 및 응답 규칙(요약)

- 공통 오류 포맷: `{ "error": "INSUFFICIENT_STOCK", "message": "재고가 부족합니다." }`
- product-service:
    - 400: 재고 부족
    - 404: 상품 없음
- order-service:
    - 400: 상태 전이 불가(예: PENDING 취소 시도)
    - 424: 상품 서비스 실패
