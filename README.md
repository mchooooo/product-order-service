# product-order-service
# 프로젝트 개요

- **목표**: 주문 서버(order-service)와 상품 서버(product-service)를 분리(MSA 입문)하여, 주문 생성/취소/조회와 상품 CRUD 및 주문에 따른 재고 증감 흐름을 구현한다.
- **통신**: 동기 REST api (필수), 이후 단계에서 Kafka 등 비동기 확장 가능(선택).
- **DB**: 서비스별 H2 DB(로컬) — 운영 단계에서 MySQL/Postgres 전환 권장.
- **핵심 원칙**: 서비스 경계 분리, 데이터베이스 분리, 멱등성(Idempotency), 원자적 재고 갱신, 실패/재시도 내결함성.

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
