# ADR-0002: 트랜잭션 경계를 서비스 메서드 단위로 선언한다

## Status
Accepted (2026-05-17)

## Context
`OrderService.createOrder()`는 재고 차감 → 포인트 차감 → 주문 저장 → 위시 삭제를 순서대로 수행한다. 기존 코드에는 `@Transactional`이 없어서, 포인트 부족으로 예외가 발생해도 이미 차감된 재고가 복구되지 않았다.

트랜잭션을 어디에 선언할지 결정해야 한다.

## Decision
`@Transactional`을 **서비스 클래스 레벨이 아닌 메서드 단위**로 선언한다. 현재는 `OrderService.createOrder()`에만 적용한다.

- 읽기 전용 메서드(`findByMemberId`)에는 불필요한 트랜잭션을 열지 않는다.
- 향후 다른 서비스에 트랜잭션이 필요해지면, 해당 메서드에 개별 선언한다.

## Consequences
- 포인트 부족 시 재고 차감이 롤백되어 데이터 정합성이 보장된다.
- 트랜잭션 범위가 명시적이다 — 메서드 시그니처만 보고 트랜잭션 여부를 알 수 있다.
- 클래스 레벨 선언 대비 새 메서드 추가 시 트랜잭션을 빠뜨릴 위험이 있다. 다만 현재 규모에서는 메서드가 적어 문제되지 않는다.
- 트랜잭션 롤백 검증 테스트(`OrderTransactionTest`)는 `Propagation.NOT_SUPPORTED`로 별도 클래스에서 수행한다. `IntegrationTest`의 `@Transactional`이 프로덕션 트랜잭션을 감싸면 롤백 여부를 관찰할 수 없기 때문이다.

## Alternatives Considered
- **클래스 레벨 `@Transactional`**
  - 거절. 읽기 전용 메서드에도 불필요한 트랜잭션이 열린다. `@Transactional(readOnly = true)` 기본값 + 쓰기 메서드 오버라이드 방식도 가능하지만, 현재 서비스 규모에서 과도하다.
- **컨트롤러 레벨 트랜잭션**
  - 거절. 컨트롤러는 HTTP 요청/응답 변환의 책임만 가진다. 비즈니스 트랜잭션 경계는 서비스에 두는 것이 관심사 분리에 맞다.
