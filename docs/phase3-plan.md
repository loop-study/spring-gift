# Phase 3 — 작동 변경 구현 플랜

## 구현 순서

작동 변경은 Red-Green 패턴으로 진행한다. 먼저 실패하는 테스트를 작성한 뒤(Red) 코드를 수정하여 통과시킨다(Green).

### Step 1: 글로벌 예외 핸들러 + 커스텀 예외 도입

**목표**: 흩어진 `@ExceptionHandler`를 통합하고, `IllegalArgumentException` 남용을 커스텀 예외로 교체한다.

#### 커스텀 예외 계층 구조

```
BusinessException (abstract, RuntimeException)
├── EntityNotFoundException        → 404
├── DuplicateEntityException       → 409
├── ValidationException            → 400
├── InsufficientStockException     → 400
├── InsufficientPointException     → 400
├── AuthenticationException        → 401
└── ForbiddenException             → 403
```

#### 세부 단계

| Sub-step | 유형 | 내용 | 작동 변경 |
|----------|------|------|-----------|
| **1-1** | feat | `BusinessException` + 7개 서브클래스 생성 | 없음 (아직 사용 안 함) |
| **1-2** | test→feat | `GlobalExceptionHandler` 도입 + 3곳 로컬 `@ExceptionHandler` 제거 | OrderController 500→400 |
| **1-3** | test→feat | Product/Category null 반환 → `EntityNotFoundException` 전환 | 없음 (이미 404) |
| **1-4** | test→feat | Option/Order/Wish null 반환 → `EntityNotFoundException` 전환 | 없음 (이미 404) |
| **1-5** | test→feat | Auth null → `AuthenticationException`, 소유권 → `ForbiddenException` | 없음 (이미 401/403) |
| **1-6** | test→feat | `IllegalArgumentException` → 커스텀 예외 (로그인 실패→401, 중복→409) | 로그인 실패 400→401, 중복 이메일 400→409 |
| **1-7** | feat | `ErrorResponse` DTO 도입 (status, message 필드) | 응답 body에 에러 메시지 추가 |

#### 작동 변경이 발생하는 곳 (Red-Green 필수)

1. **Step 1-2**: 재고 초과 주문 등 OrderController에서 `IllegalArgumentException` 시 500 → 400
2. **Step 1-6**: 로그인 실패 400→401, 중복 이메일 가입 400→409
3. **Step 1-7**: 에러 응답 body가 빈 값 → `{"status": 400, "message": "..."}`

#### 주의사항

- `@RestControllerAdvice(annotations = RestController.class)`로 SSR 컨트롤러(`@Controller`) 제외
- 제거 대상: ProductController, MemberController, OptionController의 `handleIllegalArgument` 메서드
- Step 1-3~1-5는 외부 작동 변경 없음 (컨트롤러가 이미 null→404/401/403 처리), 내부 예외 방식만 변경
- 기존 테스트 37개는 매 커밋마다 전체 통과 확인

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
