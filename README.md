# product-order-service

## 프로젝트 개요

주문 서비스(`orders-service`)와 상품 서비스(`product-service`)를 분리하여,
주문 생성/취소/조회와 상품 CRUD, 주문에 따른 재고 증감 흐름을 구현한 프로젝트입니다.

로컬 환경에서는 서비스별 H2 DB를 사용하고,
서비스 간 통신은 버전에 따라 동기 REST(OpenFeign), 비동기 메시징(RabbitMQ)을 사용합니다.

## 기능

- 상품 CRUD
- 주문 생성, 취소, 조회
- 주문에 따른 재고 차감/복구
- 동기 호출 기반 주문 처리
- RabbitMQ 기반 비동기 주문 처리
- Transactional Outbox 패턴 기반 메시지 발행
- Redis 선감소 기반 재고 처리 분기
- 재고 차감 요청 멱등성 처리
- 실패 주문 재처리 API
- 실패/종료된 Outbox 재발행 API
- 상품 서비스 소비 실패 시 DLQ 격리

## 서비스 구성

### product-service

- 상품 관리
- 재고 차감/복구 처리
- Redis 기반 선감소 처리
- RabbitMQ 소비 및 결과 이벤트 발행


### orders-service

- 주문 생성/취소/조회
- Saga 기반 주문 상태 관리
- Outbox 저장 및 스케줄러 기반 메시지 발행
- 실패 주문 재처리 및 Outbox 재발행 기능


## 주문 처리 흐름

### V1. 동기 처리

1. 주문 요청 수신
2. 주문을 `PENDING`으로 저장
3. `product-service`에 재고 차감 API 호출
4. 성공 시 `CONFIRMED`, 실패 시 `FAILED`

### V3. 비동기 처리

1. 주문 요청 수신
2. 주문과 Outbox 이벤트를 함께 저장
3. 스케줄러가 Outbox를 읽어 RabbitMQ로 발행
4. `product-service`가 메시지를 소비해 재고 차감 처리
5. 처리 결과 이벤트를 `orders-service`가 수신
6. 성공 시 `CONFIRMED`, 실패 시 `FAILED`

## 장애 대응 및 복구 기능

### Outbox 재시도

- 발행 실패 시 Outbox 상태를 `FAILED`로 변경하고 재시도 횟수를 증가시킵니다.
- 다음 재시도 시각(`nextAttemptAt`) 이후 다시 발행을 시도합니다.
- 최대 재시도 횟수를 초과하면 `DEAD` 상태로 전환합니다.

### 실패 주문 재처리

- `FAILED` 상태 주문을 다시 `PENDING`으로 되돌리고
- 재고 차감 요청 Outbox를 새로 생성하는 내부 API를 제공합니다.

### Outbox 재발행

- `FAILED` 또는 `DEAD` 상태의 Outbox를 다시 `PENDING`으로 되돌려
- 스케줄러가 재발행할 수 있도록 합니다.

### DLQ 처리

- `product-service`에서 재고 차감 처리 중 시스템 예외가 발생하면
  해당 메시지를 DLQ로 이동시켜 원본 큐에서 반복 실패하지 않도록 구성했습니다.
- 재고 부족과 같은 비즈니스 실패는 DLQ로 보내지 않고 결과 이벤트만 반환합니다.

## 기술 포인트

- 서비스별 DB 분리
- 주문/재고 간 최종 일관성 확보를 위한 Saga 방식 적용
- Transactional Outbox 패턴으로 메시지 유실/고아 메시지 위험 완화
- Redis 선감소와 DB 반영을 조합한 재고 처리
- 멱등성 키 기반 중복 재고 차감 방지
- RabbitMQ DLQ를 통한 장애 메시지 격리

## 향후 개선 아이디어

- DLQ 조회 및 재처리용 관리자 기능 추가
- 주문/Outbox 복구 이력 조회 기능 추가
- 주문 상태 세분화
- 주문 생성 자체에 대한 멱등성 처리
- 테스트 및 문서 정리 보강
