# Phase 5 — 보안 강화 및 구조 개선 플랜

## 현황 (OWASP Top 10 기반 점검 결과)

| OWASP | 항목 | 상태 | 영향 |
|---|---|---|---|
| A01 | 접근 제어 | 문제 | `/admin/**` 인증/인가 없음 — 누구나 회원 삭제/포인트 충전 가능 |
| A02 | 암호화 실패 | 문제 | 비밀번호 평문 저장 — `equals()`로 비교 |
| A05 | 보안 설정 오류 | 주의 | Spring Security 미사용, CORS/CSRF 미설정 |
| A07 | 인증 실패 | 주의 | 로그인 시도 횟수 제한 없음 |
| A09 | 로깅/모니터링 | 부분 | 카카오 API만 로깅. 인증/주문 등 핵심 이벤트 로깅 없음 |

## 의존성 결정

`spring-boot-starter-security` 대신 `spring-security-crypto`만 추가한다.

- 기존 JWT 기반 자체 인증 체계를 유지한다.
- `spring-boot-starter-security`는 자동 설정이 모든 엔드포인트에 인증을 적용하여 기존 동작을 깨뜨린다.
- 어드민 접근 제어는 `HandlerInterceptor`로 별도 구현한다.

## Step 구성

### Step 1 — 비밀번호 해싱 (BCrypt) [OWASP A02]

**목표**: 비밀번호를 평문이 아닌 BCrypt 해시로 저장/비교한다.

**변경 내용**:
- `build.gradle.kts`에 `spring-security-crypto` 의존성 추가
- `MemberService.register()`: `BCryptPasswordEncoder.encode()` 적용
- `MemberService.login()`: `BCryptPasswordEncoder.matches()` 적용
- `MemberService.createMember()` (어드민): 동일하게 해싱 적용
- `MemberService.update()` (어드민): 비밀번호 변경 시 해싱 적용
- Flyway 시드 데이터(`V2__Insert_default_data.sql`)의 비밀번호를 BCrypt 해시로 변경

**검증**: 기존 회원가입/로그인 테스트 통과 확인. 평문 비밀번호로 로그인 시 실패 확인.

### ~~Step 2 — 어드민 페이지 접근 제어 [OWASP A01]~~ (생략)

**생략 사유**: 제대로 구현하려면 관리자 역할 테이블, 세션/쿠키 기반 로그인(SSR이므로 JWT 헤더 방식 부적합), 로그인 페이지 UI가 필요하여 범위가 크다. 반쪽짜리 구현은 보안 착각만 만들고, 실제 보호 대상인 `/api/**`는 이미 JWT로 보호되고 있다. 어드민 페이지는 데이터 관리용 보조 도구로 간주하고, 향후 프론트엔드 분리 시 역할 기반 접근 제어와 함께 도입한다.

### Step 3 — 도메인 간 의존성 정리 [구조 개선]

**목표**: `OrderService`가 다른 도메인의 Repository를 직접 참조하는 구조를 개선하여, 각 도메인의 Service를 통해 접근하도록 한다.

**현황**: `OrderService`가 `OptionRepository`, `MemberRepository`, `WishRepository`를 직접 주입받아 사용 중. 도메인 경계를 넘어 Repository에 직접 접근하면 해당 도메인의 비즈니스 로직을 우회하게 된다.

**변경 내용**:
- `OrderService`의 Repository 의존성을 Service 의존성으로 교체
- `OptionService`에 재고 차감 메서드 추가
- `MemberService`에 포인트 차감 메서드 추가
- `WishService`에 위시 삭제 메서드 추가
- `OrderService`는 조율(orchestration) 역할만 수행

**검증**: 구조 변경, 기존 테스트 전체 통과 확인

### Step 4 — 인증 코드 중복 제거 [구조 개선]


**목표**: `@RequestHeader("Authorization") + extractMember()` 반복 패턴을 제거한다.

**변경 내용**:
- `LoginMember` 커스텀 어노테이션 생성
- `LoginMemberArgumentResolver` 구현 (`HandlerMethodArgumentResolver`)
- `WebMvcConfigurer`에 리졸버 등록
- `WishController` (3곳), `OrderController` (2곳) 적용

**변경 전**:
```java
@GetMapping
public ResponseEntity<?> getOrders(
    @RequestHeader("Authorization") String authorization, Pageable pageable) {
    var member = authenticationResolver.extractMember(authorization);
```

**변경 후**:
```java
@GetMapping
public ResponseEntity<?> getOrders(@LoginMember Member member, Pageable pageable) {
```

**검증**: 구조 변경, 기존 테스트 전체 통과 확인

### Step 5 — 핵심 이벤트 로깅 [OWASP A09]

**목표**: 인증, 주문, 예외 등 핵심 이벤트를 로그로 추적할 수 있게 한다.

**변경 내용**:
- `MemberService`: 회원가입 성공, 로그인 성공/실패 로그
- `OrderService`: 주문 생성 로그
- `GlobalExceptionHandler`: 예외 발생 시 `log.warn` (4xx), `log.error` (5xx 해당 예외)

**검증**: 구조 변경, 외부 작동 동일

### Step 6 — 서비스 메서드명 비즈니스 스타일로 변경 [구조 개선]

**목표**: 서비스 계층의 메서드명이 JPA Repository 메서드명(`findById`, `findAll`, `save`, `delete`)을 그대로 따르고 있어, 서비스가 Repository의 단순 위임처럼 보이는 문제를 해소한다.

**변경 내용**:
- `CategoryService`: findById→getCategory, findAll→getAllCategories, create→addCategory, update→updateCategory, delete→removeCategory
- `ProductService`: findById→getProduct, findAll→getAllProducts, findCategoryById→getCategoryById, findAllCategories→getAllCategories, create→addProduct, update→updateProduct, delete→removeProduct
- `OptionService`: findByProductId→getProductOptions, create→addOption, delete→removeOption
- `MemberService`: findById→getMember, findAll→getAllMembers, update→updateMember, delete→removeMember
- `WishService`: findById→getWish, findByMemberId→getMemberWishes
- `OrderService`: findByMemberId→getMemberOrders
- 각 서비스의 호출부(Controller, 다른 Service) 일괄 수정

**검증**: 구조 변경, 기존 테스트 전체 통과 확인

### Step 7 — MemberService 책임 분리 [구조 개선]

**목표**: `MemberService`가 회원 도메인, 인증, 카카오 OAuth 세 가지 책임을 갖고 있어 단일 책임 원칙을 위반한다. 컨트롤러는 이미 `MemberController`, `KakaoAuthController`, `AdminMemberController`로 분리되어 있으므로, 서비스도 역할에 맞게 분리한다.

**현황**: `MemberService`가 `JwtProvider`, `BCryptPasswordEncoder`, `KakaoLoginClient`, `KakaoLoginProperties` 4개의 외부 의존성을 보유. 회원 CRUD와 무관한 인증/OAuth 로직이 혼재.

**변경 내용**:
- `AuthService` 추출: `register()`, `login()` + `JwtProvider`, `BCryptPasswordEncoder` 의존성 이전
- `KakaoAuthService` 추출: `buildKakaoAuthUrl()`, `kakaoLogin()` + `KakaoLoginClient`, `KakaoLoginProperties` 의존성 이전
- `MemberService`: 회원 CRUD(`getMember`, `getAllMembers`, `createMember`, `updateMember`, `removeMember`) + 포인트(`deductPoint`, `chargePoint`)만 유지
- 각 컨트롤러가 자기 역할에 맞는 서비스만 참조하도록 변경

**검증**: 구조 변경, 기존 테스트 전체 통과 확인

### Step 8 — WishService 로깅 추가 [OWASP A09]

**목표**: WishService에 핵심 이벤트 로그를 추가하여 운영 가시성을 확보한다. Step 5에서 MemberService, OrderService, GlobalExceptionHandler에 로깅을 추가했지만 WishService는 누락된 상태.

**변경 내용**:
- 위시 추가 성공 시 `log.info` (memberId, productId)
- 위시 삭제 성공 시 `log.info` (wishId, memberId)
- 소유권 검증 실패 시 `log.warn` (wishId, memberId)

**검증**: 구조 변경, 외부 작동 동일

### Step 9 — 누락된 서비스 로깅 보완 [OWASP A09]

**목표**: Step 5, Step 8에서 로깅을 추가했지만, CategoryService, ProductService, OptionService, KakaoAuthService에는 로그가 없다. 어드민 상품/카테고리 변경, 카카오 로그인 등 운영에 필요한 이벤트를 추적할 수 있도록 보완한다.

**변경 내용**:
- `CategoryService`: 카테고리 추가/수정/삭제 시 `log.info`
- `ProductService`: 상품 추가/수정/삭제 시 `log.info`
- `OptionService`: 옵션 추가/삭제/재고 차감 시 `log.info`
- `KakaoAuthService`: 카카오 로그인 성공 시 `log.info`

**검증**: 구조 변경, 외부 작동 동일

### Step 10 — AuthenticationResolver MemberRepository 직접 참조 제거 [구조 개선]

**목표**: `AuthenticationResolver`가 `MemberRepository`를 직접 참조하고 있어 도메인 의존성 원칙에 어긋난다. `MemberService`를 통해 접근하도록 변경한다.

**변경 내용**:
- `AuthenticationResolver`: `MemberRepository` → `MemberService` 의존성 교체
- `@Autowired` 어노테이션 제거 (다른 컴포넌트와 스타일 통일)

**검증**: 구조 변경, 기존 테스트 전체 통과 확인

### Step 11 — 삭제 메서드 존재 확인 추가 [코드 리뷰]

**목표**: `removeCategory`, `removeProduct`, `removeMember`가 `deleteById`를 직접 호출하여, 존재하지 않는 ID 삭제 시 `EmptyResultDataAccessException`이 `GlobalExceptionHandler`에서 처리되지 않아 500이 반환되는 문제를 해소한다.

**변경 내용**:
- `CategoryService.removeCategory()`: 삭제 전 `getCategory(id)` 호출로 존재 확인
- `ProductService.removeProduct()`: 삭제 전 `getProduct(id)` 호출로 존재 확인
- `MemberService.removeMember()`: 삭제 전 `getMember(id)` 호출로 존재 확인

**검증**: 구조 변경, 존재하지 않는 ID 삭제 시 404 응답 확인

### Step 12 — 예외 메시지 언어 통일 [코드 리뷰]

**목표**: 예외 메시지가 한국어와 영어로 혼재되어 있어, 운영 환경에서 `grep`이나 모니터링 시 혼동된다. 한국어로 통일한다.

**현황**:
- 영어: `"Invalid email or password."`, `"Email is already registered."`, `"Member not found."`, `"Amount must be greater than zero."`
- 한국어: `"카테고리가 존재하지 않습니다."`, `"포인트가 부족합니다."`, `"이미 존재하는 옵션명입니다."` 등

**변경 내용**:
- `AuthService`: `"Invalid email or password."` → `"이메일 또는 비밀번호가 올바르지 않습니다."`
- `MemberService`: `"Email is already registered."` → `"이미 등록된 이메일입니다."`, `"Member not found."` → `"회원이 존재하지 않습니다."`
- `Member.chargePoint()`: `"Amount must be greater than zero."` → `"충전 금액은 1 이상이어야 합니다."`
- `AdminMemberController`: 에러 메시지 동일하게 한국어 적용

**검증**: 구조 변경, 외부 작동 동일 (메시지 텍스트만 변경)

### ~~Step 13 — Order.calculateTotalPrice() 오버플로 방지~~ (생략)

**생략 사유**: `int` 범위(약 21억)에서 오버플로가 발생하려면 단일 주문에서 가격 3,360,000 × 수량 640 이상이어야 한다. 현실적으로 발생할 수 없는 시나리오이며, 필요하다면 `long` 전환보다 주문 수량 상한을 두는 것이 더 자연스럽다.

### Step 14 — Wish 테이블 unique constraint 추가 [코드 리뷰]

**목표**: wish 테이블에 `(member_id, product_id)` unique constraint가 없어, 애플리케이션 레벨의 중복 체크만으로 데이터 정합성을 보장하고 있다. DB 레벨에서 중복을 방지하여 데이터 무결성을 강화한다.

**변경 내용**:
- Flyway 마이그레이션 `V3__Add_wish_unique_constraint.sql` 추가
- `Wish` 엔티티에 `@Table(uniqueConstraints)` 명시

**검증**: 기존 테스트 전체 통과 확인

### 코드 리뷰에서 유지로 결정한 항목

| 항목 | 유지 사유 |
|---|---|
| **AuthService/KakaoAuthService → MemberRepository 직접 참조** | 이전 세션에서 "같은 auth→member 방향이므로 괜찮다"로 결정. 순환 의존 아님. |
| **OptionService.getProductOptions() 상품 존재 확인** | 존재하지 않는 상품 ID에 대해 빈 리스트(200) 대신 404를 반환하는 것이 REST 의미론에 부합. 의도적 설계. |

## 고려했으나 제외한 것

| 항목 | 제외 사유 |
|---|---|
| **CORS 설정** | 현재 같은 도메인에서만 사용. 프론트엔드 분리 시 추가 |
| **로그인 시도 제한** | 과제 규모에서 과도. Rate limiter 인프라 필요 |
| **spring-boot-starter-security** | 자동 설정이 기존 JWT 인증 체계를 깨뜨림. BCrypt만 필요하므로 `spring-security-crypto` 선택 |
