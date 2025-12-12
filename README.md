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
## Redis 도입 (DB 앞단의 부하 차단)

- 목적
  - 한정판 / 핫 상품처럼 짧은 시간에 요청이 폭주하는 상황에서 모든 요청이 곧장 DB로 들어가면 특정 상품 row에 락 경합이 집중되어 성능 저하와 타임아웃이 발생할 수 있다.
  - 이를 완화하기 위해 Redis에 한정판 재고 카운터를 두고 DECRBY을 통해 선착순으로 재고를 예약한 요청만 넘긴다.
  - 이미 소진된 이후의 요청은 애플리케이션 레벨에서 즉시 매진 처리하여 DB 부하를 줄이는 구조
  - DB까지 갈 필요 없는 요청을 Redis에서 빠르게 걸러낸다.
  - DB 한 줄(row)에 몰리는 트래픽을 입구 컷으로 해결한다.

- 핵심 개념
  - Redis의 연산 (DECRBY, INCRBY)을 이용한 재고 선감소
  - 매진된 이후엔 DB, product-service, 사가 로직까지 아예 요청이 도달하지 않게 함

- 효과
  - 늦게 도착한 트래픽은 DB까지 요청이 가지 않음
  - 사가 흐름을 그대로 유지하고 트래픽 대응
 
- 향후 계획
  - Redis + MQ 도입
    - Redis에서 살아남은(선착순 성공한) 요청을 모두 DB로 즉시 보내지 않고
    - MQ(Kafka)에 쌓아두고 product-service가 처리할 수 있는 속도대로 처리하도록 한다.
