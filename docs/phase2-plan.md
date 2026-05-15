# Phase 2 — 서비스 계층 추출 플랜

## 추출 순서

의존성이 없는 도메인부터 추출하고, 의존이 많은 도메인을 마지막에 한다.
각 커밋 시점에 37개 통합 테스트가 전부 통과해야 한다.

| 순서 | 서비스 | 대상 컨트롤러 | 주입 의존성 |
|------|--------|-------------|------------|
| 1 | CategoryService | CategoryController | CategoryRepository |
| 2 | MemberService | MemberController, KakaoAuthController, AdminMemberController | MemberRepository, JwtProvider, KakaoLoginClient, KakaoLoginProperties |
| 3 | ProductService | ProductController, AdminProductController | ProductRepository, CategoryRepository |
| 4 | OptionService | OptionController | OptionRepository, ProductRepository |
| 5 | WishService | WishController | WishRepository, ProductRepository |
| 6 | OrderService | OrderController | OrderRepository, OptionRepository, MemberRepository, WishRepository, KakaoMessageClient |

## 원칙

- **작동 변경 금지**: 로직을 그대로 옮기기만 한다.
- **@Transactional 추가 금지**: Phase 3에서 별도로 추가한다.
- **예외 타입 보존**: IllegalArgumentException, NoSuchElementException 그대로 유지.
- **null 반환 패턴 유지**: REST 컨트롤러의 null 체크 → 404 패턴 보존.
- **AuthenticationResolver는 컨트롤러에 유지**: 서비스는 memberId 또는 Member 객체를 파라미터로 받는다.

## 컨트롤러에 남는 것

- 요청 파싱 (`@RequestBody`, `@PathVariable`, `@RequestParam`)
- 인증 처리 (`AuthenticationResolver`)
- 응답 변환 (`XxxResponse.from()`)
- HTTP 상태 결정 (`ResponseEntity`)
- `@ExceptionHandler`

## 공통 로직 통합

### MemberService
- MemberController(회원가입/로그인)와 KakaoAuthController(카카오 로그인)가 `JwtProvider.createToken()` 패턴을 공유한다.
- AdminMemberController의 회원 CRUD도 동일 서비스로 통합한다.

### ProductService
- ProductController(REST)와 AdminProductController(SSR)가 상품 CRUD를 공유한다.
- 차이점: Admin은 `ProductNameValidator.validate(name, true)` (카카오 허용), REST는 `validate(name)` (카카오 불허).
- AdminProductController는 `ProductNameValidator`를 직접 호출하여 폼 검증 후 서비스를 호출한다.

## 커밋 계획

```
refactor: extract CategoryService from CategoryController
refactor: extract MemberService from member and auth controllers
refactor: extract ProductService from product controllers
refactor: extract OptionService from OptionController
refactor: extract WishService from WishController
refactor: extract OrderService from OrderController
```
