# 레거시 예외 처리 현황 분석

## 현재 상태 분석

### 1. `IllegalArgumentException`을 던지는 모든 위치

| 위치 | 메서드 | 용도 | 현재 HTTP 매핑 |
|------|--------|------|----------------|
| ProductService:99 | `validateName()` | 상품명 검증 실패 (15자 초과, "카카오" 포함 등) | 400 (로컬 핸들러) |
| OptionService:36 | `create()` | 중복 옵션명 | 400 (로컬 핸들러) |
| OptionService:50 | `delete()` | 옵션 1개인 상품에서 삭제 시도 | 400 (로컬 핸들러) |
| OptionService:64 | `validateName()` | 옵션명 검증 실패 (50자 초과, 특수문자 등) | 400 (로컬 핸들러) |
| MemberService:34 | `register()` | 이메일 중복 | 400 (로컬 핸들러) |
| MemberService:42 | `login()` | 존재하지 않는 이메일 | 400 (로컬 핸들러) |
| MemberService:44 | `login()` | 비밀번호 불일치 | 400 (로컬 핸들러) |
| MemberService:92 | `findById()` | 회원 미존재 (Admin 전용) | 핸들러 없음 → 500 |
| Member:51 | `chargePoint()` | 충전 금액 0 이하 | 핸들러 없음 → 500 |
| Member:58 | `deductPoint()` | 차감 금액 0 이하 | 핸들러 없음 → 500 |
| Member:61 | `deductPoint()` | 포인트 부족 | 핸들러 없음 → 500 |
| Option:41 | `subtractQuantity()` | 재고 부족 | 핸들러 없음 → 500 |

### 2. `NoSuchElementException`을 던지는 모든 위치

| 위치 | 메서드 | 용도 | 현재 HTTP 매핑 |
|------|--------|------|----------------|
| ProductService:36 | `findByIdOrThrow()` | 상품 미존재 | Admin SSR 전용 (Whitelabel 에러) |
| ProductService:44 | `findCategoryByIdOrThrow()` | 카테고리 미존재 | Admin SSR 전용 |

> 이 두 메서드는 `AdminProductController`에서만 호출되며 SSR 컨트롤러이므로 `@RestControllerAdvice` 범위 밖이다.

### 3. null 반환 후 컨트롤러에서 404 처리하는 모든 위치

| 서비스 메서드 | 컨트롤러 위치 | 패턴 |
|---------------|---------------|------|
| `ProductService.findById()` → null | ProductController:37 `GET /{id}` | `if (product == null) return 404` |
| `ProductService.create()` → null | ProductController:47 `POST /` | `if (saved == null) return 404` (카테고리 미존재) |
| `ProductService.update()` → null | ProductController:59 `PUT /{id}` | `if (saved == null) return 404` (상품 또는 카테고리 미존재) |
| `CategoryService.update()` → null | CategoryController:47 `PUT /{id}` | `if (category == null) return 404` |
| `OptionService.findByProductId()` → null | OptionController:33 `GET /` | `if (options == null) return 404` |
| `OptionService.create()` → null | OptionController:48 `POST /` | `if (saved == null) return 404` (상품 미존재) |
| `OptionService.delete()` → false | OptionController:62 `DELETE /{optionId}` | `if (!deleted) return 404` |
| `WishService.findProductById()` → null | WishController:53 `POST /` | `if (product == null) return 404` |
| `WishService.findById()` → null | WishController:79 `DELETE /{id}` | `if (wish == null) return 404` |
| `OrderService.createOrder()` → null | OrderController:52 `POST /` | `if (saved == null) return 404` (옵션 미존재) |

### 4. 컨트롤러에서 직접 401, 403 반환하는 모든 위치

| 컨트롤러 | 메서드 | 상태 코드 | 조건 |
|----------|--------|-----------|------|
| OrderController:34 | `getOrders()` | 401 | `authenticationResolver.extractMember() == null` |
| OrderController:46 | `createOrder()` | 401 | 동일 |
| WishController:36 | `getWishes()` | 401 | 동일 |
| WishController:50 | `addWish()` | 401 | 동일 |
| WishController:73 | `removeWish()` | 401 | 동일 |
| WishController:83 | `removeWish()` | 403 | `wish.getMemberId() != member.getId()` |

### 5. `@ExceptionHandler`가 있는 3곳

| 위치 | 대상 예외 | 반환 |
|------|-----------|------|
| ProductController:71-74 | `IllegalArgumentException` | 400 + `e.getMessage()` |
| MemberController:40-43 | `IllegalArgumentException` | 400 + `e.getMessage()` |
| OptionController:68-71 | `IllegalArgumentException` | 400 + `e.getMessage()` |

> 모두 동일한 구현이다: `ResponseEntity.badRequest().body(e.getMessage())`

---

## 커스텀 예외 설계

현재 `IllegalArgumentException` 하나로 검증 실패, 중복, 인증 실패, 재고 부족, 포인트 부족을 모두 표현하고 있다. 이를 의미별로 분리한다.

### 필요한 커스텀 예외 계층

```
gift.exception.BusinessException (abstract, RuntimeException)
├── gift.exception.EntityNotFoundException    → 404 Not Found
├── gift.exception.DuplicateEntityException   → 409 Conflict
├── gift.exception.ValidationException        → 400 Bad Request
├── gift.exception.InsufficientStockException → 400 Bad Request
├── gift.exception.InsufficientPointException → 400 Bad Request
├── gift.exception.AuthenticationException    → 401 Unauthorized
└── gift.exception.ForbiddenException         → 403 Forbidden
```

`BusinessException`은 message 필드를 가지는 공통 부모이며, `GlobalExceptionHandler`에서 각 타입별로 HTTP 상태 코드를 매핑한다.

> **주의**: `InsufficientStockException`과 `InsufficientPointException`을 `ValidationException`에 합칠 수도 있지만,
> 향후 주문 도메인에서 재고/포인트 문제를 구분하여 처리해야 할 가능성
> (예: 트랜잭션 롤백 후 사용자에게 구체적 안내)이 있으므로 분리하는 것이 낫다.

---

## 세분화된 Sub-step 플랜

### Step 1-1: 커스텀 예외 클래스 정의

**무엇을 변경하는가**: `gift.exception` 패키지를 생성하고 7개의 커스텀 예외 클래스를 추가한다. 순수 추가이며 기존 코드에는 일절 손대지 않는다.

**before → after 작동 차이**: 없음. 예외 클래스만 추가하므로 기존 동작에 영향 없다.

**변경 대상 파일 목록**:

- `src/main/java/gift/exception/BusinessException.java` (신규)
- `src/main/java/gift/exception/EntityNotFoundException.java` (신규)
- `src/main/java/gift/exception/DuplicateEntityException.java` (신규)
- `src/main/java/gift/exception/ValidationException.java` (신규)
- `src/main/java/gift/exception/InsufficientStockException.java` (신규)
- `src/main/java/gift/exception/InsufficientPointException.java` (신규)
- `src/main/java/gift/exception/AuthenticationException.java` (신규)
- `src/main/java/gift/exception/ForbiddenException.java` (신규)

**테스트 전략**: 기존 테스트 전체가 그대로 통과하는지 확인 (regression only). 새 예외 클래스 자체는 로직이 없으므로 별도 단위 테스트 불필요.

**커밋 메시지**:

```
feat(exception): add custom exception class hierarchy

Add BusinessException base class and 7 specific subclasses
(EntityNotFoundException, DuplicateEntityException, ValidationException,
InsufficientStockException, InsufficientPointException,
AuthenticationException, ForbiddenException) under gift.exception package.
No existing code is modified.
```

---

### Step 1-2: GlobalExceptionHandler 도입 + 로컬 `@ExceptionHandler` 제거

**무엇을 변경하는가**: `@RestControllerAdvice(annotations = RestController.class)` 글로벌 핸들러를 추가하고, 3곳의 동일한 로컬 `@ExceptionHandler(IllegalArgumentException.class)` 메서드를 제거한다. 글로벌 핸들러에서 `IllegalArgumentException` → 400, `NoSuchElementException` → 404를 매핑한다. 이 단계에서는 커스텀 예외 매핑도 함께 등록하지만, 아직 커스텀 예외를 throw하는 코드는 없으므로 해당 핸들러는 아직 사용되지 않는다.

**before → after 작동 차이**: `OrderController`에서 `IllegalArgumentException` 발생 시 (재고 부족, 포인트 부족) 기존 **500 → 400**으로 변경. 이것이 이 커밋의 핵심 작동 변경이다. `ProductController`, `MemberController`, `OptionController`는 기존과 동일하게 400을 반환한다 (핸들러가 로컬에서 글로벌로 이동했을 뿐).

**변경 대상 파일 목록**:

- `src/main/java/gift/exception/GlobalExceptionHandler.java` (신규)
- `src/main/java/gift/product/ProductController.java` (`handleIllegalArgument` 메서드 제거)
- `src/main/java/gift/member/MemberController.java` (`handleIllegalArgument` 메서드 제거)
- `src/main/java/gift/option/OptionController.java` (`handleIllegalArgument` 메서드 제거)

**테스트 전략 (Red → Green)**:

- **Red**: 재고 초과 주문 테스트를 `OrderControllerTest`에 추가한다. user1 (포인트 5,000,000)이 옵션 id=1 (스페이스 블랙 / M1 Pro, 수량 10)을 11개 주문하면 `Option.subtractQuantity()`가 `IllegalArgumentException`을 던진다. 현재는 글로벌 핸들러가 없어 500이 반환되므로, 400을 기대하는 테스트는 실패한다(Red).
- **Green**: `GlobalExceptionHandler`를 도입하고 로컬 핸들러 3개를 제거하면 새 테스트가 통과한다. 기존 테스트 전체도 통과해야 한다.

**커밋 2개**:

```
test(order): verify stock exceeded order returns 400

Add test: 재고_초과_주문시_400을_반환한다
Currently fails because OrderController has no IllegalArgumentException
handler (returns 500).
```

```
feat(exception): add GlobalExceptionHandler, remove local handlers

Introduce @RestControllerAdvice for all @RestController classes.
Map IllegalArgumentException to 400 and NoSuchElementException to 404.
Remove duplicate @ExceptionHandler from ProductController,
MemberController, and OptionController.
```

---

### Step 1-3: 서비스 계층 null 반환 → `EntityNotFoundException` throw로 변경 (Product, Category)

**무엇을 변경하는가**: `ProductService`와 `CategoryService`에서 엔티티를 못 찾았을 때 null을 반환하는 패턴을 `EntityNotFoundException`을 throw하는 패턴으로 변경한다. `ProductController`와 `CategoryController`에서 null 체크 분기를 제거한다.

구체적으로:

- `ProductService.findById()`: `orElse(null)` → `orElseThrow(() -> new EntityNotFoundException(...))`
- `ProductService.findCategoryById()`: 동일하게 변경
- `ProductService.create(ProductRequest)`: 내부에서 null 체크 대신 `findCategoryByIdOrThrow` 패턴 사용
- `ProductService.update(Long, ProductRequest)`: 동일
- `CategoryService.update()`: `orElse(null)` → `orElseThrow`

> **참고**: `ProductService`에는 이미 `findByIdOrThrow()`, `findCategoryByIdOrThrow()`가 있지만 이들은 `NoSuchElementException`을 던진다. 이들도 `EntityNotFoundException`으로 교체한다. 그러면 `findById()`와 `findByIdOrThrow()`가 동일해지므로 `findByIdOrThrow()`를 삭제하고 `findById()`에 통합한다. `AdminProductController`에서 `findByIdOrThrow()` 호출을 `findById()`로 변경한다.

**before → after 작동 차이**: 외부에서 보이는 HTTP 응답은 동일하다 (404). 단, 내부적으로 null 분기가 사라지고 예외 기반 흐름으로 바뀐다. `GlobalExceptionHandler`가 `EntityNotFoundException` → 404로 매핑하므로 동일한 결과.

**변경 대상 파일 목록**:

- `src/main/java/gift/product/ProductService.java` (`findById`/`findCategoryById`를 throw로 변경, `findByIdOrThrow` 제거, `create`/`update`에서 null 반환 제거)
- `src/main/java/gift/product/ProductController.java` (null 체크 3곳 제거)
- `src/main/java/gift/product/AdminProductController.java` (`findByIdOrThrow` → `findById` 호출 변경)
- `src/main/java/gift/category/CategoryService.java` (`update`에서 null 반환 제거)
- `src/main/java/gift/category/CategoryController.java` (null 체크 1곳 제거)
- `src/main/java/gift/exception/GlobalExceptionHandler.java` (이미 Step 1-2에서 `EntityNotFoundException` → 404 매핑 등록 완료)

**테스트 전략 (Red → Green)**:

- **Red**: `ProductControllerTest`에 기존 `존재하지_않는_카테고리로_생성하면_404를_반환한다` 테스트의 응답 body에 에러 메시지가 포함되는지 검증하는 테스트를 추가한다. 현재 null 반환 → `ResponseEntity.notFound().build()`는 body가 비어 있으므로 실패한다.
- **Green**: 서비스에서 `EntityNotFoundException`을 throw하고 `GlobalExceptionHandler`가 메시지를 body에 담아 404를 반환하면 통과한다.

**커밋 2개**:

```
test(product): verify 404 response includes error message body

Add assertion: 존재하지_않는_상품_조회시_에러_메시지를_반환한다
Currently fails because null-return pattern produces empty 404 body.
```

```
refactor(product,category): replace null returns with EntityNotFoundException

ProductService and CategoryService now throw EntityNotFoundException
instead of returning null. Remove null-check branches from
ProductController and CategoryController. Merge findByIdOrThrow into
findById in ProductService.
```

---

### Step 1-4: 서비스 계층 null 반환 → `EntityNotFoundException` throw로 변경 (Option, Order, Wish)

**무엇을 변경하는가**: `OptionService`, `OrderService`, `WishService`에서 null/false 반환 패턴을 `EntityNotFoundException` throw로 변경한다.

구체적으로:

- `OptionService.findByProductId()`: `orElse(null)` → `orElseThrow` (상품 미존재)
- `OptionService.create()`: 동일
- `OptionService.delete()`: 상품 미존재, 옵션 미존재 시 false 대신 `EntityNotFoundException` throw. 반환 타입 `boolean` → `void`
- `OrderService.createOrder()`: 옵션 `orElse(null)` → `orElseThrow`
- `WishService.findProductById()`: `orElse(null)` → `orElseThrow`
- `WishService.findById()`: `orElse(null)` → `orElseThrow`

**before → after 작동 차이**: HTTP 응답은 동일 (404). 내부 흐름이 null 분기에서 예외 기반으로 변경.

**변경 대상 파일 목록**:

- `src/main/java/gift/option/OptionService.java`
- `src/main/java/gift/option/OptionController.java` (null 체크 3곳 제거)
- `src/main/java/gift/order/OrderService.java`
- `src/main/java/gift/order/OrderController.java` (null 체크 1곳 제거)
- `src/main/java/gift/wish/WishService.java`
- `src/main/java/gift/wish/WishController.java` (null 체크 2곳 제거)

**테스트 전략**: 기존 테스트가 모두 통과하는지 regression 확인. 404 관련 테스트는 이미 존재한다: `존재하지_않는_상품의_옵션을_조회하면_404를_반환한다`, `존재하지_않는_옵션으로_주문하면_404를_반환한다`, `존재하지_않는_상품에_위시를_추가하면_404를_반환한다`. 추가 Red 테스트는 필요 없다 (작동 변경 없음, 내부 리팩터링).

**커밋 메시지**:

```
refactor(option,order,wish): replace null returns with EntityNotFoundException

OptionService, OrderService, and WishService now throw
EntityNotFoundException instead of returning null/false.
Remove null-check branches from corresponding controllers.
```

---

### Step 1-5: 인증/인가 예외 도입 (`AuthenticationException`, `ForbiddenException`)

**무엇을 변경하는가**: `AuthenticationResolver.extractMember()`에서 null 대신 `AuthenticationException`을 throw하도록 변경한다. `WishController.removeWish()`에서 소유권 검증 실패 시 `ForbiddenException`을 throw하도록 변경한다. 컨트롤러에서 401/403 수동 반환 분기를 제거한다.

구체적으로:

- `AuthenticationResolver.extractMember()`: catch 블록과 `orElse(null)` → `AuthenticationException("유효하지 않은 토큰입니다.")` throw
- `WishController.removeWish()`: `wish.getMemberId() != member.getId()` → `throw new ForbiddenException("다른 사용자의 위시를 삭제할 수 없습니다.")`
- `OrderController`: 두 메서드에서 `if (member == null) return 401` 제거 (예외가 자동 전파)
- `WishController`: 세 메서드에서 `if (member == null) return 401` 제거 + 403 분기 제거

**before → after 작동 차이**: HTTP 응답은 동일 (401, 403). 다만 이전에는 비어있던 응답 body에 에러 메시지가 포함된다.

**변경 대상 파일 목록**:

- `src/main/java/gift/auth/AuthenticationResolver.java`
- `src/main/java/gift/order/OrderController.java` (401 분기 2곳 제거)
- `src/main/java/gift/wish/WishController.java` (401 분기 3곳 + 403 분기 1곳 제거)

**테스트 전략**: 기존 테스트 regression으로 충분하다. `유효하지_않은_토큰으로_주문하면_401을_반환한다`, `유효하지_않은_토큰으로_위시를_조회하면_401을_반환한다`, `다른_사용자의_위시를_삭제하면_403을_반환한다`가 이미 존재한다. 추가 Red 테스트 불필요 (HTTP 상태 코드 변경 없음).

**커밋 메시지**:

```
refactor(auth): replace null-return auth with AuthenticationException

AuthenticationResolver now throws AuthenticationException instead of
returning null. WishController throws ForbiddenException for ownership
violation. Remove manual 401/403 response branches from OrderController
and WishController.
```

---

### Step 1-6: 도메인 엔티티의 `IllegalArgumentException` → 커스텀 예외로 교체

**무엇을 변경하는가**: 도메인 엔티티(`Member`, `Option`)와 서비스(`MemberService`, `OptionService`)에서 던지는 `IllegalArgumentException`을 의미에 맞는 커스텀 예외로 교체한다.

구체적 매핑:

| 위치 | 변경 전 | 변경 후 |
|------|---------|---------|
| `Member.chargePoint()` | `IllegalArgumentException` | `ValidationException("Amount must be greater than zero.")` |
| `Member.deductPoint()` (금액 검증) | `IllegalArgumentException` | `ValidationException("차감 금액은 1 이상이어야 합니다.")` |
| `Member.deductPoint()` (포인트 부족) | `IllegalArgumentException` | `InsufficientPointException("포인트가 부족합니다.")` |
| `Option.subtractQuantity()` | `IllegalArgumentException` | `InsufficientStockException("차감할 수량이 현재 재고보다 많습니다.")` |
| `MemberService.register()` | `IllegalArgumentException` | `DuplicateEntityException("Email is already registered.")` |
| `MemberService.login()` (2곳) | `IllegalArgumentException` | `AuthenticationException("Invalid email or password.")` |
| `OptionService.create()` | `IllegalArgumentException` | `DuplicateEntityException("이미 존재하는 옵션명입니다.")` |
| `OptionService.delete()` | `IllegalArgumentException` | `ValidationException("옵션이 1개인 상품은 옵션을 삭제할 수 없습니다.")` |
| `ProductService.validateName()` | `IllegalArgumentException` | `ValidationException(String.join(...))` |
| `OptionService.validateName()` | `IllegalArgumentException` | `ValidationException(String.join(...))` |

**before → after 작동 차이**:

- `MemberService.login()` 실패: 기존 **400 → 401**. 잘못된 비밀번호는 인증 실패이므로 401이 의미적으로 올바르다.
- `MemberService.register()` 중복 이메일: 기존 **400 → 409** Conflict. 의미적으로 더 정확하다.
- 나머지는 모두 동일하게 400 유지.

**변경 대상 파일 목록**:

- `src/main/java/gift/member/Member.java`
- `src/main/java/gift/option/Option.java`
- `src/main/java/gift/member/MemberService.java`
- `src/main/java/gift/option/OptionService.java`
- `src/main/java/gift/product/ProductService.java`

**테스트 전략 (Red → Green)**:

- **Red**: `MemberControllerTest`에서 두 테스트를 수정한다.
  - `잘못된_비밀번호로_로그인하면_400을_반환한다` → 기대값을 **401**로 변경 → 현재 400이므로 Red.
  - `중복_이메일로_회원가입하면_400을_반환한다` → 기대값을 **409**로 변경 → 현재 400이므로 Red.
- **Green**: 서비스/엔티티에서 커스텀 예외를 throw하면 `GlobalExceptionHandler`가 401/409로 매핑하여 통과.

> **중요**: 로그인 실패 시 401로의 변경은 의도된 작동 변경이다. 기존 400은 "잘못된 요청" 의미이고, 401은 "인증 실패" 의미이므로 HTTP 시맨틱에 더 부합한다.

**커밋 3개**:

```
test(member): expect 401 for login failure, 409 for duplicate email

Change expected status: login failure 400 → 401 (authentication failure),
duplicate registration 400 → 409 (conflict). Both tests now fail (Red).
```

```
refactor(domain): replace IllegalArgumentException with custom exceptions

Member, Option entities and MemberService, OptionService, ProductService
now throw semantically correct custom exceptions: DuplicateEntityException
for conflicts, AuthenticationException for login failures,
InsufficientStockException/InsufficientPointException for resource
shortages, ValidationException for input validation.
```

```
refactor(exception): remove IllegalArgumentException handler from GlobalExceptionHandler

All IllegalArgumentException throw sites have been migrated to custom
exceptions. Remove the catch-all IllegalArgumentException handler.
```

> **참고**: 마지막 커밋(`IllegalArgumentException` 핸들러 제거)은 선택적이다. 방어적으로 남겨둘 수도 있지만, 예상치 못한 `IllegalArgumentException`이 400으로 흡수되어 디버깅이 어려워지는 문제가 있다. 제거하면 미처리된 `IllegalArgumentException`이 500으로 노출되어 빠르게 발견할 수 있다. 이 판단은 구현 시 결정한다.

---

### Step 1-7: ErrorResponse DTO 통일

**무엇을 변경하는가**: 현재 `GlobalExceptionHandler`가 `ResponseEntity<String>`으로 에러 메시지를 반환하고 있는데, 이를 구조화된 `ErrorResponse` DTO로 통일한다.

```java
public record ErrorResponse(String message) {}
```

모든 핸들러의 반환 타입을 `ResponseEntity<ErrorResponse>`로 변경한다.

**before → after 작동 차이**: 에러 응답 body가 plain text `"메시지"` 에서 `{"message": "메시지"}` JSON 형태로 변경된다.

**변경 대상 파일 목록**:

- `src/main/java/gift/exception/ErrorResponse.java` (신규)
- `src/main/java/gift/exception/GlobalExceptionHandler.java` (반환 타입 변경)

**테스트 전략**: 기존 테스트 중 body 내용을 검증하는 테스트가 없으므로 (status code만 검증) regression만으로 충분하다. 추가로 `GlobalExceptionHandlerTest` 단위 테스트를 작성하여 각 예외별 응답 형태를 검증한다.

**커밋 메시지**:

```
feat(exception): add structured ErrorResponse DTO for error responses

Introduce ErrorResponse record and update GlobalExceptionHandler to
return JSON error responses instead of plain text strings.
```

---

## 전체 커밋 순서 요약

| Sub-step | 커밋 수 | Red/Green | 핵심 변경 |
|----------|---------|-----------|-----------|
| 1-1 | 1 | - | 커스텀 예외 클래스 7개 추가 (순수 추가) |
| 1-2 | 2 | Red → Green | GlobalExceptionHandler + 재고 초과 400 테스트 |
| 1-3 | 2 | Red → Green | Product/Category null → EntityNotFoundException |
| 1-4 | 1 | - | Option/Order/Wish null → EntityNotFoundException |
| 1-5 | 1 | - | Auth null → AuthenticationException/ForbiddenException |
| 1-6 | 2~3 | Red → Green | IllegalArgumentException → 커스텀 예외 전체 교체 |
| 1-7 | 1 | - | ErrorResponse DTO 통일 |
| **합계** | **10~11** | | |

---

## 의존 관계와 순서 제약

```
1-1 (예외 클래스 정의)
 └── 1-2 (글로벌 핸들러 도입) -- 이것이 인프라
      ├── 1-3 (Product/Category null → throw)
      │    └── 1-4 (Option/Order/Wish null → throw)
      ├── 1-5 (Auth null → throw) -- 1-3, 1-4와 독립
      └── 1-6 (IllegalArgumentException → 커스텀 예외) -- 1-3~1-5 이후 권장
           └── 1-7 (ErrorResponse DTO) -- 맨 마지막
```

> 1-3, 1-4, 1-5는 서로 독립적이어서 순서를 바꿀 수 있지만, 같은 패턴(null → throw)의 변환이므로 도메인 단위로 묶어서 순서대로 진행하는 것이 코드 리뷰하기에 좋다.
