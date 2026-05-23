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

### Step 2 — 어드민 페이지 접근 제어 [OWASP A01]

**목표**: `/admin/**` 경로에 인증을 요구한다.

**변경 내용**:
- `AdminAuthInterceptor` 도입 — JWT 토큰 검증 또는 세션 기반 인증
- `WebMvcConfigurer`에 인터셉터 등록 (`/admin/**` 경로)
- 인증 실패 시 로그인 페이지로 리다이렉트

**검증**: 인증 없이 `/admin/products` 접근 시 리다이렉트 확인

**논의 필요**: 어드민 인증 방식 — JWT 헤더 vs 세션/쿠키. SSR 페이지이므로 세션 기반이 자연스러움.

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

## 고려했으나 제외한 것

| 항목 | 제외 사유 |
|---|---|
| **CORS 설정** | 현재 같은 도메인에서만 사용. 프론트엔드 분리 시 추가 |
| **로그인 시도 제한** | 과제 규모에서 과도. Rate limiter 인프라 필요 |
| **spring-boot-starter-security** | 자동 설정이 기존 JWT 인증 체계를 깨뜨림. BCrypt만 필요하므로 `spring-security-crypto` 선택 |
