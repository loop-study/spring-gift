# Phase 3 — 작동 변경 구현 플랜

## 구현 순서

작동 변경은 Red-Green 패턴으로 진행한다. 먼저 실패하는 테스트를 작성한 뒤(Red) 코드를 수정하여 통과시킨다(Green).

### Step 1: @RestControllerAdvice 글로벌 예외 핸들러

**작동 변경**: OrderController 등 핸들러 없던 곳에서 IllegalArgumentException 시 500 → 400

| 커밋 | 유형 | 내용 |
|------|------|------|
| 1-a | test (Red) | 재고 초과 주문 시 400 기대하는 테스트 추가 |
| 1-b | feat (Green) | GlobalExceptionHandler 도입 + 3곳 로컬 @ExceptionHandler 제거 |

- `@RestControllerAdvice(annotations = RestController.class)`로 SSR 컨트롤러(@Controller) 제외
- 제거 대상: ProductController, MemberController, OptionController의 handleIllegalArgument 메서드

### Step 2: @Transactional 도입

**작동 변경**: 포인트 부족 시 재고 차감이 롤백됨 (기존에는 재고만 깎이고 복구 안 됨)

| 커밋 | 유형 | 내용 |
|------|------|------|
| 2-a | test (Red) | 포인트 부족 시 재고 롤백 검증 테스트 |
| 2-b | feat (Green) | OrderService.createOrder()에 @Transactional 추가 |

- 롤백 검증 테스트는 별도 클래스로 분리
- `@Transactional(propagation = NOT_SUPPORTED)`로 테스트 트랜잭션 비활성화 필요

### Step 3: Wish cleanup 구현

**작동 변경**: 주문 생성 시 해당 상품이 위시리스트에 있으면 자동 제거

| 커밋 | 유형 | 내용 |
|------|------|------|
| 3-a | test (Red) | 주문 후 위시 개수 감소 검증 테스트 |
| 3-b | feat (Green) | OrderService.createOrder()에 wish 삭제 로직 추가 |

- OrderService에 이미 WishRepository가 주입되어 있음
- `wishRepository.findByMemberIdAndProductId().ifPresent(delete)` 패턴

### Step 4: 가격 계산 도메인 이전

**작동 변경**: 총액 계산을 서비스가 아닌 Order 도메인이 담당

| 커밋 | 유형 | 내용 |
|------|------|------|
| 4-a | test (Red) | Order.calculateTotalPrice() 단위 테스트 |
| 4-b | feat (Green) | Order에 메서드 추가 + OrderService에서 사용 |

- Order 인스턴스를 save 전에 생성하면 기존 순서(포인트 차감 → 주문 저장) 유지 가능

### Step 5: 카카오 메시지 트랜잭션 분리

**작동 변경**: 카카오 알림이 트랜잭션 커밋 후에만 발송됨

| 커밋 | 유형 | 내용 |
|------|------|------|
| 5-a | feat | OrderCreatedEvent + OrderNotificationListener 도입, 이벤트 발행으로 교체 |
| 5-b | test | 롤백 시 카카오 미발송 검증 테스트 |

- `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용
- ApplicationEventPublisher로 이벤트 발행

## 주의사항

1. **테스트 트랜잭션 충돌**: IntegrationTest의 @Transactional이 프로덕션 트랜잭션을 감싸므로, 롤백 검증 시 별도 테스트 클래스 + NOT_SUPPORTED 전파 필요
2. **@RestControllerAdvice 범위**: annotations = RestController.class로 SSR 컨트롤러 제외
3. **시드 데이터 의존**: user1 포인트 5,000,000 + 맥북 가격 3,360,000 조합으로 포인트 부족 시나리오 구성
4. **ADR 작성 후보**: 트랜잭션 경계, 카카오 메시지 발송 시점, 글로벌 예외 핸들러 범위
