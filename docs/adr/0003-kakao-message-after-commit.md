# ADR-0003: 카카오 메시지 발송을 트랜잭션 커밋 이후로 분리한다

## Status
Accepted (2026-05-17)

## Context
`OrderService.createOrder()`는 주문 저장 후 카카오 메시지를 발송한다. 기존 코드는 서비스 메서드 안에서 `KakaoMessageClient.sendToMe()`를 직접 호출했다.

`@Transactional`을 도입하면서(ADR-0002) 두 가지 문제가 드러났다.

1. **롤백 후에도 메시지가 발송된다.** 재고 차감 후 포인트 부족으로 롤백되더라도, 이미 호출된 HTTP 요청은 되돌릴 수 없다.
2. **외부 API 실패가 주문을 롤백시킨다.** 카카오 서버 장애 시 주문 자체가 실패하는 것은 과도하다.

## Decision
`ApplicationEventPublisher`로 `OrderCreatedEvent`를 발행하고, `@TransactionalEventListener(phase = AFTER_COMMIT)`으로 수신하여 카카오 메시지를 발송한다.

- 이벤트는 트랜잭션 내에서 발행하되, 리스너는 커밋 성공 후에만 실행된다.
- 리스너에서 발생하는 예외는 catch하여 무시한다 — 알림 실패가 주문 결과에 영향을 주지 않는다.
- 카카오 액세스 토큰이 없는 회원(일반 가입)은 리스너 진입 시 즉시 반환한다.

## Consequences
- 트랜잭션 롤백 시 메시지가 발송되지 않는다. `OrderTransactionTest`에서 `verify(kakaoMessageClient, never())`로 검증했다.
- 카카오 API 장애가 주문 성공/실패에 영향을 주지 않는다.
- `OrderService`는 카카오 메시지 발송 로직을 알지 못한다 — 관심사가 분리된다.
- 이벤트 발행과 리스너가 같은 프로세스 내에서 동기적으로 실행되므로, 메시지 유실(프로세스 크래시) 가능성이 있다. 현재 규모에서는 허용 가능하다.

## Alternatives Considered
- **서비스 메서드 내 직접 호출 + try-catch**
  - 거절. 롤백 후에도 메시지가 발송되는 문제를 해결하지 못한다.
- **`@Async`로 비동기 발송**
  - 거절. 비동기만으로는 트랜잭션 커밋 전 발송 문제를 해결하지 못한다. `@TransactionalEventListener`와 조합하면 가능하지만, 현재 규모에서 비동기 스레드 풀 관리는 과도하다.
- **Outbox 패턴 (별도 테이블에 메시지 저장 후 배치 발송)**
  - 거절. 메시지 유실을 완전히 방지하지만, 별도 스케줄러와 테이블이 필요하다. 카카오 알림은 best-effort 수준이면 충분하다.
