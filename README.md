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

## 핵심 흐름 V1 (OpenFeign 통신)

1. **주문 접수**: `POST /orders` 수신 → 주문 `PENDING` 저장
2. **동기 통신(OpenFeign)**: `PATCH /products/{id}/stock` 호출(멱등키=DEC-{orderId}, INC-{orderId})
3. **상태 확정**: 응답이 `success=true` → 주문 `CONFIRMED`, `success=false` 또는 오류 → 주문 `FAILED` + 사유 기록

## 핵심 흐름 V2 (Redis + MQ)

1. **주문 접수**: `POST /orders` 호출 시 주문을 `PENDING`으로 저장하고 즉시 응답합니다.
2. **비동기 통신**: `RabbitMQ`를 통해 상품 서버로 재고 차감 이벤트를 발행합니다.
3. **멱등성 보장**: 상품 서버는 멱등성 키를 사용하여 동일한 멱등성에 대한 중복 재고 차감을 방지합니다.
4. **상태 확정**: 상품 서버로부터 처리 결과를 메시지로 수신하여 주문을 `CONFIRMED` 또는 `FAILED`로 전환합니다.

---

## 상태 전이 규칙 V1

```smalltalk
PENDING --(decrease 성공)--> CONFIRMED --(취소)--> CANCELLED
│
└--(decrease 실패/에러)--> FAILED
```

- 취소는 `CONFIRMED`에서만 허용. 취소 실패 시 주문은 `CONFIRMED` 유지

## 상태 전이 규칙 V2

```smalltalk
        
PENDING -----(decrease 성공)----------> CONFIRMED
  │                                                                             
  │                                                                       
  ├----------(decrease 실패/에러)------> FAILED
  │        (재고 부족 / 상품 서버 에러등)                           
  │                                                                                                                                                                                     
  └--(중복 요청: 멱등성 확인)--> (이전 요청에 대한 상태 유지)               
```

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
## Redis 도입 (DB 부하 감소)

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
 
---
## Message Queue 도입 (비동기 재고 처리 시스템)

- 도입 배경
  - 시스템 결합도 증가: 상품 서버가 다운될 경우 주문 서버까지 영향을 받아 전체 서비스가 장애로 이어짐.
  - 응답 속도 저하: 주문 완료를 위해 상품 서버의 재고 차감 응답을 끝까지 기다려야 하므로 사용자 경험 저하.
 
- 해결 방안
  - RabbitMQ를 도입하여 서버 간 통신을 비동기(Asynchronous) 방식으로 구현.
  - Event-Driven 아키텍처: 주문이 발생하면 OrderEvent를 발행(Publish)하고, 상품 서버는 이를 구독(Consume)하여 재고를 차감.
  - 관심사 분리: 주문 서버는 '주문 수 접수'에만 집중하고, 재고 처리는 상품 서버의 책임으로 넘겨 결합도를 낮춤.
 
- 발생한 이슈
  - Docker 컨테이너 간 통신 문제 (Connection refused)
    - 문제: 도커 컨테이너 내부에서 RabbitMQ 호스트를 localhost로 인식하여 상품 서버가 연결에 실패하는 문제 발생.
    - 해결: docker-compose.yml 내에 SPRING_RABBITMQ_HOST=rabbitmq 환경 변수를 주입하고, 도커 내부 브릿지 네트워크(product-net)를 통해 서비스 이름으로 통신하도록 개선.
   
  - Exchange-Queue 바인딩 및 메시지 유실 문제
    - 문제: 주문 서버에서 메시지를 발행했으나 상품 서버의 리스너가 동작하지 않음. 확인 결과, 상품 서버에서 큐와 익스체인지를 연결하는 Binding 설정 시 사용한 Routing Key가 주문 서버의 발행 키와 일치하지 않아 메시지가 큐로 전달되지 않음.
    - 해결: 일단 간단하게 주문 서버와 상품 서버 설정 값을 확인하고 맞춰줌. 많은 서버에서 mq를 사용한다면 공통 모듈을 만들어 관리하는 것을 생각해 볼 수 있음.
