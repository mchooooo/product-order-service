# product-order-service
# 프로젝트 개요

- **목표**: 주문 서버(order-service)와 상품 서버(product-service)를 분리(MSA 입문)하여, 주문 생성/취소/조회와 상품 CRUD 및 주문에 따른 재고 증감 흐름을 구현한다.
- **통신**: 동기 REST api (필수), 이후 단계에서 Kafka 등 비동기 확장 가능(선택).
- **DB**: 서비스별 H2 DB(로컬) — 운영 단계에서 MySQL/Postgres 전환 권장.
- **핵심 원칙**: 데이터베이스 분리, 멱등성(Idempotency), 주문 서버와 상품 서버의 상태 일관성(사가 패턴), 상품 재고 감소 보장.

---
## 엔티티 설계, API 명세
### product-service
  [product-service README.md](https://github.com/mchooooo/product-order-service/blob/main/product-service/README.md)
### order-service
  [order-service README.md](https://github.com/mchooooo/product-order-service/blob/main/orders-service/README.md)
  
---
## 기능

### product-service

- 상품 CRUD
- 주문에 의한 재고 증가
- 주문에 의한 재고 감소

### order-service

- 주문 생성
- 주문 취소
- 주문 조회


---

## 핵심 흐름 (시퀀스)

1. `POST /orders` 수신 → 주문 `PENDING` 저장
2. `PATCH /products/{id}/stock` 호출(멱등키=DEC-{orderId}, INC-{orderId})
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

- 공통 오류 포맷: `{ "error": "INSUFFICIENT_STOCK", "message": "재고가 부족합니다.", "details": null }`
- product-service:
    - 400: 재고 부족
    - 404: 상품 없음
    - 503: 락 경합/타임아웃
- order-service:
    - 400: 상태 전이 불가(예: PENDING 취소 시도)
    - 424: 상품 서비스 실패
 
---

## 주문 서버 Saga패턴 도입
- 목적
  - 주문 생성/취소 과정에서 주문 서버와 상품 서버 간 최종 일관성 확보
  - 부분 실패 시 보상 트랜잭션으로 상태 복구
  - 외부 의존 장애(5xx/타임아웃) 시 재시도 전략 적용
 
- 핵심 설계
  - 오케스트레이터 방식: OrderSagaOrchestrator
  - 단계
    - ✅ PENDING 주문 생성 (로컬 트랜잭션)
    - ✅ 상품 서버에 재고 차감(DEC-{orderId}) 호출 (Idempotency-Key 포함)
    - ✅ 성공 → 주문을 CONFIRMED로 전이
    - ✅ 실패(상품 서버 4xx) → 주문을 FAILED로 전이
    - ❌ 장애(5xx/타임아웃) → 재시도 정책 (아직 구현 X)
  - 보상(Compensation)
    - 주문 도중 예외/알 수 없는 예외(RuntimeException) 발생 시 재고 증가(INC-{orderId}) 보상 호출
    - 취소 실패 시 주문 상태는 스펙대로 CONFIRMED 유지
- 추가하고 싶은 내용
  - 이벤트 발행 방식 구현

 
---
