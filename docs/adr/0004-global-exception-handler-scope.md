# ADR-0004: 글로벌 예외 핸들러를 @RestController 범위로 제한한다

## Status
Accepted (2026-05-17)

## Context
Phase 3 Step 1에서 각 컨트롤러에 분산된 `@ExceptionHandler`를 하나의 `GlobalExceptionHandler`로 통합했다. 이 과정에서 두 가지 제약이 있었다.

1. **SSR 컨트롤러와 REST 컨트롤러 공존.** `AdminProductController`는 `@Controller`로 Thymeleaf 뷰를 반환한다. `@RestControllerAdvice`를 무조건 전역 적용하면 SSR 컨트롤러의 에러 응답도 JSON으로 바뀐다.
2. **커스텀 예외 계층 필요.** 기존 코드는 `IllegalArgumentException` 하나로 인증 실패, 중복 데이터, 유효성 검증 등을 모두 처리했다. HTTP 상태 코드를 정확히 매핑하려면 예외를 분류해야 한다.

## Decision
### 예외 핸들러 범위
`@RestControllerAdvice(annotations = RestController.class)`로 선언하여, `@RestController`가 붙은 클래스에서 발생한 예외만 처리한다.

### 커스텀 예외 계층
`BusinessException`(abstract)을 루트로 7개 서브클래스를 둔다.

| 예외 | HTTP 상태 | 용도 |
|---|---|---|
| `EntityNotFoundException` | 404 | 조회 결과 없음 |
| `DuplicateEntityException` | 409 | 중복 데이터 (이메일, 옵션명) |
| `ValidationException` | 400 | 도메인 유효성 검증 실패 |
| `InsufficientStockException` | 400 | 재고 부족 |
| `InsufficientPointException` | 400 | 포인트 부족 |
| `AuthenticationException` | 401 | 인증 실패 |
| `ForbiddenException` | 403 | 소유권/권한 부족 |

### 에러 응답 형식
`ErrorResponse` record로 통일한다: `{ "message": "에러 메시지" }`.

## Consequences
- SSR 컨트롤러(`AdminProductController`)는 기존 동작을 유지한다. JSON 에러 응답이 HTML 페이지에 노출되지 않는다.
- 예외 클래스만 보고 HTTP 상태 코드를 예측할 수 있다 — 컨트롤러마다 상태 코드 매핑을 반복하지 않는다.
- 기존 `IllegalArgumentException`을 사용하던 곳을 모두 전환하면서, 일부 HTTP 상태 코드가 변경되었다: 로그인 실패 400→401, 중복 이메일/옵션명 400→409.
- 새로운 예외 유형이 필요하면 `BusinessException`을 상속하고 핸들러에 매핑을 추가해야 한다. 다만 7개 분류가 현재 도메인을 충분히 커버한다.

## Alternatives Considered
- **`@RestControllerAdvice` 무조건 전역 적용**
  - 거절. `AdminProductController`(`@Controller`)에도 JSON 에러가 반환되어 SSR 페이지가 깨진다.
- **`basePackages`로 범위 제한**
  - 거절. 패키지 구조 변경에 취약하다. `annotations` 필터는 어노테이션 기반이라 패키지 이동에 영향받지 않는다.
- **`IllegalArgumentException` 유지 + 메시지 파싱으로 상태 코드 분기**
  - 거절. 메시지 문자열에 의존하는 분기는 깨지기 쉽고, 새 에러 유형 추가 시 핸들러 수정이 필요하다.
- **Spring `ResponseStatusException` 사용**
  - 거절. 서비스 계층에서 HTTP 상태 코드를 직접 지정하게 되어, 도메인 로직과 웹 계층의 결합이 생긴다.
