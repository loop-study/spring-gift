# spring-gift

선물하기 서비스 — 레거시 코드 리팩터링과 작동 변경.

## 진행 방식

- 한 커밋은 하나의 의도만 담는다.
- **구조 변경**(리팩터링)과 **작동 변경**(기능 추가/수정)은 같은 커밋에 섞지 않는다.
- 커밋 메시지는 [AngularJS Git Commit Message Conventions](https://gist.github.com/stephenparish/9941e89d80e2bc58a153)을 따른다.
  - `feat`, `fix`, `refactor`, `docs`, `test`, `style`, `chore`
- 작동 변경은 테스트 또는 관찰 가능한 결과로 증거를 남긴다.
- 트레이드오프가 있는 결정은 [docs/adr](docs/adr)에 ADR로 기록한다.
- 현재 코드 분석은 [docs/legacy-info.md](docs/legacy-info.md)를 참고한다.

---

## 기능 목록

### Phase 1 — 테스트 환경 구축

> 목표: 누구나 동일하게 실행 가능하고, 반복 실행할 수 있는 자동화된 테스트 환경. [A2]

- [x] 테스트 인프라: `application-test.properties` 작성 (H2 in-memory + Flyway) + 스모크 테스트
- [x] Category API 통합 테스트
- [x] Member API 통합 테스트 (회원가입/로그인 → JWT)
- [x] Product API 통합 테스트
- [x] Option API 통합 테스트
- [x] Wish API 통합 테스트
- [x] Order API 통합 테스트

> 테스트 작성 순서는 도메인 의존성이 적은 순서를 따른다.
> Category/Member(의존 없음) → Product(Category 필요) → Option(Product 필요)
> → Wish(Member + Product 필요) → Order(Member + Option 필요).
> 앞 단계의 테스트 픽스처를 다음 단계에서 재사용하고,
> Phase 2 서비스 추출 시 기존 작동이 깨지지 않았음을 이 테스트로 증명한다.

### Phase 2 — 구조 변경 (작동 유지)

> 목표: 서비스 계층을 추출하고, 중복 코드를 정리한다. 기존 작동은 변경하지 않는다. [A1, C1, C2, C3]
> 상세 플랜은 [docs/phase2-plan.md](docs/phase2-plan.md)를 참고한다.

**서비스 계층 추출** — 각 도메인별 1커밋

- [x] `CategoryService` 추출 — CategoryController의 카테고리 CRUD 로직 이전
- [x] `MemberService` 추출 — MemberController 회원가입/로그인 + KakaoAuthController 카카오 로그인 로직 이전
- [x] `ProductService` 추출 — ProductController + AdminProductController 공통 로직 이전
- [x] `OptionService` 추출 — OptionController의 옵션 CRUD 로직 이전
- [x] `WishService` 추출 — WishController의 위시 추가/삭제/조회 로직 이전
- [x] `OrderService` 추출 — OrderController의 주문 생성/조회 로직 이전


### Phase 3 — 작동 변경 (증거로 검증)

> 목표: 누락된 작동을 구현하고, 데이터 정합성을 보장한다. 모든 변경은 테스트로 증명한다. [A3, B1, B2, B3, C1]
> 상세 플랜은 [docs/phase3-plan.md](docs/phase3-plan.md)를 참고한다.

**Step 1 — 글로벌 예외 핸들러 + 커스텀 예외 도입** [C1]
> 현황 분석은 [docs/legacy-exception-info.md](docs/legacy-exception-info.md)를 참고한다.

- [x] 1-1: `BusinessException` + 7개 서브클래스 생성
- [x] 1-2: `GlobalExceptionHandler` 도입 + 로컬 `@ExceptionHandler` 3곳 제거 (OrderController 500→400)
- [x] 1-3: Product/Category null 반환 → `EntityNotFoundException` 전환
- [x] 1-4: Option/Order/Wish null 반환 → `EntityNotFoundException` 전환
- [x] 1-5: Auth null → `AuthenticationException`, 소유권 → `ForbiddenException`
- [x] 1-6: `IllegalArgumentException` → 커스텀 예외 (로그인 실패 400→401, 중복 이메일/옵션명 400→409)
- [x] 1-7: `ErrorResponse` DTO 도입 (응답 body에 에러 메시지 추가)

**Step 2 — `@Transactional` 도입** [A3, B1]

- [x] 2-a: 포인트 부족 시 재고 롤백 검증 테스트 (Red)
- [x] 2-b: `OrderService.createOrder()`에 `@Transactional` 추가 (Green)

**Step 3 — Wish cleanup 구현** [B2]

- [x] 3-a: 주문 후 위시 개수 감소 검증 테스트 (Red)
- [x] 3-b: `OrderService.createOrder()`에 wish 삭제 로직 추가 (Green)

**Step 4 — 가격 계산 도메인 이전** [B3]

- [x] 4-a: `Order.calculateTotalPrice()` 단위 테스트 (Red)
- [x] 4-b: Order에 메서드 추가 + OrderService에서 사용 (Green)

**Step 5 — 카카오 메시지 트랜잭션 분리**

- [x] 5-a: `OrderCreatedEvent` + `OrderNotificationListener` 도입, 이벤트 발행으로 교체
- [x] 5-b: 롤백 시 카카오 미발송 검증 테스트

### Phase 4 — 외부 API 연동 안정화

> 목표: 외부 API(카카오) 연동의 안정성을 높인다. 타임아웃, 에러 핸들링, 로깅, 시크릿 보호.
> 상세 플랜은 [docs/phase4-plan.md](docs/phase4-plan.md)를 참고한다.

- [x] ADR 추가 작성 (트랜잭션 경계, 카카오 메시지 발송 시점, 글로벌 예외 핸들러 범위)
- [x] Step 1: 외부 API 호출 로깅 추가 (KakaoLoginClient, KakaoMessageClient, OrderNotificationListener)
- [x] Step 2: 카카오 로그인 에러 핸들링 (500→401, 에러 코드 log.warn)
- [x] Step 3: RestClient 타임아웃 설정 (connect/read 5초)
- [x] ~~Step 4: RestClient baseUrl 정리~~ (건너뜀 — URL 변경 가능성 없어 실익 없음)
- [x] Step 5: .gitignore 시크릿 보호

### Phase 5 — 보안 강화 및 구조 개선 (OWASP 기반)

> 목표: OWASP Top 10 기준으로 보안 취약점을 해소하고, 반복 코드를 정리한다.
> 상세 플랜은 [docs/phase5-plan.md](docs/phase5-plan.md)를 참고한다.

**보안 (높음)**

- [x] 비밀번호 해싱 (BCrypt) — 현재 평문 저장/비교 [OWASP A02]
- [ ] ~~어드민 페이지 접근 제어~~ — 생략. 관리자 역할 테이블+세션 로그인+UI 필요하여 범위 초과. `/api/**`는 JWT로 보호 중. 어드민은 보조 도구로 간주.

**구조 개선 (중간)**

- [x] 도메인 간 의존성 정리 — `OrderService`가 다른 도메인의 Repository를 직접 참조 → 각 도메인 Service를 통하도록 개선
- [x] 인증 코드 중복 제거 — `@RequestHeader + extractMember()` 5곳 반복 → `HandlerMethodArgumentResolver` 도입
- [x] 핵심 이벤트 로깅 — 회원가입/로그인/주문 생성, GlobalExceptionHandler 예외 로그 [OWASP A09]
- [x] 서비스 메서드명 비즈니스 스타일로 변경 — JPA Repository 종속적인 `findById`, `findAll` 등을 `getProduct`, `getAllProducts` 등으로 통일
- [x] MemberService 책임 분리 — 회원 CRUD / 인증(AuthService) / 카카오 OAuth(KakaoAuthService) 분리
- [x] WishService 로깅 추가 — 위시 추가/삭제, 소유권 검증 실패 로그
- [x] 누락된 서비스 로깅 보완 — CategoryService, ProductService, OptionService, KakaoAuthService
- [x] AuthenticationResolver MemberRepository 직접 참조 제거

**코드 리뷰 개선 (중간)**

- [x] 삭제 메서드 존재 확인 추가 — `removeCategory`, `removeProduct`, `removeMember`가 존재하지 않는 ID 삭제 시 500 반환
- [x] 예외 메시지 언어 통일 — 한국어/영어 혼재 → 한국어 통일
- [ ] ~~`Order.calculateTotalPrice()` 오버플로 방지~~ — 생략. 단일 주문 640개 이상이어야 발생하는 비현실적 시나리오. 필요 시 주문 수량 상한이 더 적절.
- [x] Wish 테이블 unique constraint 추가 — DB 레벨 중복 방지로 데이터 무결성 강화

**선택 (낮음)**

- [ ] ~~CORS 설정~~ — 생략. 현재 같은 도메인에서만 사용하므로 불필요. 프론트엔드 분리 시 도입.
- [ ] ~~로그인 시도 횟수 제한~~ — 생략. Rate limiter 인프라가 필요하여 과제 규모 초과.

### Phase 6 — 1차 코드 리뷰 반영

> 목표: PR #8 리뷰 코멘트 반영 + 로깅 횡단 처리.
> 상세 플랜은 [docs/phase6-plan.md](docs/phase6-plan.md)를 참고한다.

- [x] `@Transactional` 적용 + 불필요한 `save()` 제거
- [x] 카테고리/상품 삭제 시 연결 엔티티 확인 후 400 반환
- [x] DTO 변환을 Service 계층으로 통일
- [x] 의미없는 INFO 로그 제거 (KakaoLoginClient, KakaoMessageClient)
- [ ] ~~로깅 횡단 처리~~ — 생략. 프로젝트 규모에서 과도. 불필요한 INFO 로그는 Step 4에서 해소.
- [x] 테스트 코드 `var` → 명시적 타입 변경

---

## 구현 전략

### 순서 원칙
- Phase 1 → 2 → 3 순서를 지킨다. 서비스 계층이 없으면 트랜잭션 경계도 자연스럽지 않다.
- Phase 내에서도 체크리스트 위에서 아래로 진행한다.

### 변경 원칙
- plan 또는 README 체크리스트에 다음 작업이 정의되어 있어야 코드 수정을 시작한다.
- 구조 변경 커밋: 테스트 결과가 변경 전후로 동일해야 한다.
- 작동 변경 커밋: 먼저 실패하는 테스트(Red)를 만든 뒤 코드를 고친다(Green).
- 한 커밋에는 구조 변경 또는 작동 변경 중 하나만 담는다.

### 검증 원칙
- 변경 후 `./gradlew test` 전체 통과가 최소 기준이다.
- 작동 변경은 "예외가 안 나는 것"이 아니라 "상태 재조회로 결과를 확인"하는 것이다.
- 의도하지 않은 변경이 발견되면 즉시 되돌린다.

### ADR 트리거
다음 중 하나라도 해당하면 [docs/adr](docs/adr)에 ADR을 남긴다.
- 선택지가 2개 이상이고 트레이드오프가 존재하는 경우
- 반복적으로 따라야 할 규칙이나 경계를 정의한 경우
- 테스트 전략이나 검증 방식이 결정의 핵심이었던 경우

---

## 빌드 및 실행

```bash
./gradlew build         # 컴파일 + 테스트
./gradlew bootRun       # 로컬 실행 (H2 임베디드)
./gradlew test          # 테스트만
```

### 환경변수 (카카오 로그인 사용 시)

| 변수 | 설명 | 기본값 |
|---|---|---|
| `KAKAO_CLIENT_ID` | 카카오 REST API 키 | (없음) |
| `KAKAO_CLIENT_SECRET` | 카카오 클라이언트 시크릿 | (없음) |
| `KAKAO_REDIRECT_URI` | OAuth 리다이렉트 URI | `http://localhost:8080/api/auth/kakao/callback` |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) | 개발용 기본값 |
| `JWT_EXPIRATION` | 토큰 만료 시간(ms) | 3600000 |

> 어드민 키, 액세스 토큰, 클라이언트 시크릿은 저장소에 커밋하지 않는다.

---

## AI 활용 기록

이 과제는 Claude Code와 함께 진행한다. AI 산출물은 항상 초안으로만 다루고, 설계/검증/커밋 책임은 본인에게 있다.

### 활용 패턴

- **탐색 → 분석 → 기획 → 구현** 순서를 지킨다. 코드를 읽히고 발견사항을 정리한 뒤에만 구현에 들어간다.
- **한 조각씩 요청한다.** "다음 변경 1개"로 범위를 좁힌다.
- **구조/작동 분리를 강제한다.** 한 응답에서 두 종류 변경이 섞이면 중단하고 분리한다.
- **파일 작성 전 승인을 받는다.** AI가 제안한 내용을 먼저 확인한 뒤 파일에 반영한다.
- **검증 증거를 함께 요구한다.** 작동 변경에는 항상 테스트 결과를 붙인다.
- **설계 토론으로 방향을 잡는다.** AI가 제안하면 바로 수용하지 않고, "왜 이렇게 나눠야 해?", "이게 현실적으로 발생해?" 같은 질문으로 검증한다.
- **AI의 작업 흐름을 제어한다.** AI가 여러 작업을 한꺼번에 하려 하면 "문서 먼저 커밋하고, Step별로 커밋해"처럼 프로세스를 강제한다.
- **생략 판단도 사람이 한다.** AI가 제안한 개선이라도 실익이 없으면 사유를 남기고 건너뛴다.

### 로그

| 일자 | 단계 | 활용한 방식 | 학습/판단 |
|---|---|---|---|
| 2026-05-11 | 탐색 | 전체 소스 파일 일괄 읽기 → 엔드포인트/도메인/문제점 표 정리 | OrderController에 트랜잭션 경계 없음, wish cleanup 누락, 서비스 계층 부재를 체계적으로 발견. legacy-info.md로 분석 결과 문서화. |
| 2026-05-11 | 기획 | legacy-info.md 기반으로 Phase별 체크리스트 도출, 문제 번호와 커밋 단위를 1:1 매핑 | 구조 변경이 작동 변경의 전제임을 확인 — Phase 순서 의존성 확립. |
| 2026-05-13 | 구현 | 도메인 의존성 순서대로 통합 테스트 작성 (Category→Member→Product→Option→Wish→Order, 총 37개 테스트). IntegrationTest 추상 클래스 추출로 Spring 컨텍스트 캐싱 및 공통 헬퍼 재사용. | 테스트 작성 순서를 의존성 기반으로 정하니 시드 데이터 설계가 자연스럽게 정리됨. @Transactional 롤백 전략으로 테스트 간 격리 확보. |
| 2026-05-15 | 구현 | Plan 에이전트로 서비스 추출 플랜 수립 후 도메인별 1커밋씩 6개 서비스 추출. 매 커밋마다 37개 통합 테스트 전체 통과 확인. Explore 에이전트로 추출 전후 작동 변경 여부 교차 검증. | `@RestControllerAdvice`를 구조 변경으로 분류했다가, 전역 적용 시 기존 500 응답이 400으로 바뀌는 점을 발견하여 Phase 3(작동 변경)으로 재분류. 분류 기준을 "코드 위치"가 아닌 "사용자가 관찰 가능한 결과"로 재정립. |
| 2026-05-17 | 구현 | Explore 에이전트로 전체 예외 사용 현황 분석 → legacy-exception-info.md 문서화. Plan 에이전트로 Step 1을 7개 sub-step으로 세분화. Red-Green 패턴으로 10커밋에 걸쳐 글로벌 예외 핸들러 + 커스텀 예외 계층 도입. | 중복 옵션명도 DuplicateEntityException으로 교체 시 400→409 변경이 발생함을 테스트 실행 중 발견. 플랜에서 누락된 작동 변경을 테스트가 잡아줌 — Red-Green 패턴의 안전망 역할 확인. |
| 2026-05-21 | 기획+구현 | Phase 4 세부 플랜 수립. 카카오 에러 코드 세분화 범위를 논의 후 "클라이언트에는 단일 메시지, 내부는 로그로 구분" 방침 결정. 로깅→에러핸들링→타임아웃→시크릿 보호 순서로 5 Step 실행. | 로그가 전혀 없는 상태에서 수동 테스트 시 동작 확인 불가 — Phase 5의 로깅을 Phase 4 Step 1로 당겨서 우선 처리. Step 4(baseUrl 정리)는 "여전히 하드코딩"이라는 사용자 지적으로 건너뜀. |
| 2026-05-23 | 기획+구현 | OWASP Top 10 기반 점검 → Phase 5 플랜 수립. spring-security-crypto vs spring-boot-starter-security 트레이드오프 논의 후 crypto 선택. BCrypt 해싱 적용 + Flyway 시드 데이터 갱신. | Flyway 마이그레이션의 시드 비밀번호도 BCrypt 해시로 교체 필요 — gradle 캐시에서 JAR을 찾아 해시 생성. `.env` 방식 대신 IntelliJ 환경변수 주입 선택 (Spring Boot가 `.env` 자동 로드하지 않음). |
| 2026-05-25 | 구현 | Phase 5 구조 개선 집중 실행. 서비스 메서드명 비즈니스 스타일 전환 → MemberService 3분할 설계 토론 → ProductService 불필요한 위임 제거 → WishService 멱등성 개선 → 전 서비스 로깅 → AuthenticationResolver 의존성 정리. 매 단계마다 "문서 먼저 → 구현 → 테스트 → 커밋" 사이클. | MemberService 분리 시 `register()`가 Auth인지 Member인지 토론. "Auth는 로그인만 남기자"로 결정 — 회원 생성은 Member 책임, 인증(토큰 발급)은 Auth 책임. BCryptPasswordEncoder 중복은 `SecurityConfig` @Bean으로 해결. WishService에서 POST가 200/201 혼재하는 걸 발견하고 "멱등성이 안 지켜지는 문제"로 판단 → 항상 200 반환으로 변경. |
| 2026-05-27 | 리뷰+개선 | `/review` 명령으로 브랜치 전체 코드 리뷰 실행. 6개 개선 제안 도출 후 사용자가 항목별로 실행/생략 판단. 문서 커밋 → 각 Step별 코드 변경+테스트+커밋 순서 강제. | AI가 4개 Step을 한 번에 구현하려 하자 "왜 한꺼번에 진행하려고 해? 문서부터 커밋하고 각 스탭마다 커밋해야지"로 프로세스 교정. `int` 오버플로 제안에 "포인트를 21억 이상 줄 경우가 존재할까?"로 반박 → 생략. Wish unique constraint 논의에서 "히스토리처럼 관리할 수 있다는거 아닐까?" 질문 → 현재 모델이 상태 기반(타임스탬프·소프트삭제 없음)임을 확인 후 진행. |

### 실패 사례와 교훈

#### WishService 추출 시 불필요한 설계 변경 혼입

**문제 정의**: Phase 2는 "코드를 그대로 옮기기만 한다"가 원칙이다. 그러나 AI가 WishService를 추출하면서 원본에 없던 `AddResult` record와 `RemoveResult` enum을 새로 도입했다. 다른 5개 서비스는 null 반환, boolean 반환 등 원본 패턴을 유지했는데, WishService만 새로운 타입 설계가 섞였다.

**발생 원인**: AI에게 서비스 추출을 일괄 요청하면서, 결과물의 패턴 일관성을 커밋 전에 검토하지 않았다. 테스트가 통과한다는 사실에 안심하여 "외부 작동이 같으면 된다"고 넘어갔지만, 한 커밋에 **추출 + 설계 변경**이 섞인 것은 프로젝트의 커밋 원칙 위반이다.

**교훈**:
- 테스트 통과 ≠ 원칙 준수. 테스트는 외부 작동만 검증하고, 커밋 원칙(구조/작동 분리)은 사람이 직접 확인해야 한다.
- AI 산출물을 승인할 때 "다른 서비스와 패턴이 동일한가?"를 체크리스트에 넣어야 한다.
- 새로운 타입(enum, record) 도입은 설계 결정이므로, 단순 추출 커밋과 분리해야 한다.

#### HTTP 테스트 파일에 존재하지 않는 API 포함

**문제 정의**: AI가 IntelliJ HTTP Client 테스트 파일(`http/api-test.http`)을 생성하면서, 실제로 존재하지 않는 `PUT /api/members/point` 엔드포인트를 포함시켰다. 포인트 충전은 어드민 전용 `POST /admin/members/{id}/charge-point`로만 존재한다.

**발생 원인**: AI가 "포인트 충전 기능이 있을 것"이라는 추론으로 일반적인 REST 패턴의 엔드포인트를 만들어냈다. 실제 컨트롤러를 확인하지 않고 "그럴듯한" API를 생성한 전형적인 환각이다.

**교훈**:
- AI가 생성한 API 호출은 실제 컨트롤러/라우팅과 대조해야 한다.
- 테스트 코드(`.http` 파일 포함)도 코드 리뷰 대상이다. "테스트니까 괜찮겠지"는 위험하다.
- 존재하지 않는 API를 호출하면 404가 돌아오므로 실행해보면 잡히지만, 실행 없이 커밋하면 그대로 남는다.

#### WishController에 비즈니스 로직 잔존

**문제 정의**: Phase 2에서 서비스 계층을 추출할 때 "코드를 그대로 옮기기만 한다"는 원칙을 따랐다. 그러나 Phase 3에서 작동 변경을 진행하면서, `WishController`에 남아 있는 비즈니스 로직(위시 중복 체크, 소유권 검증)을 서비스로 내리는 작업을 누락했다.

**발생 원인**: AI에게 Phase 3 세부 기획을 지시할 때 "컨트롤러에 남은 비즈니스 로직 정리"를 명시하지 않았다. AI는 지시받은 범위(예외 핸들러, 트랜잭션, 위시 삭제, 가격 계산, 카카오 메시지)만 기획에 포함했고, 사람도 검토에서 놓쳤다.

**교훈**:
- AI에게 기획을 맡기더라도, 전체 코드를 훑어보며 누락된 개선 대상이 없는지 사람이 확인해야 한다.
- 추상적인 지시("작동 변경 기획해줘")로는 AI가 모든 개선 대상을 찾아내지 못한다.

#### AI가 여러 Step을 한 번에 구현 시도

**문제 정의**: 코드 리뷰 개선 제안 4개(Step 11~14)를 Phase 5에 추가한 뒤, AI가 문서 커밋 없이 4개 Step의 코드를 한꺼번에 수정하기 시작했다. 문서 변경과 코드 변경이 섞이고, Step 간 경계 없이 일괄 진행하려 했다.

**발생 원인**: "개선하자"라는 지시를 받으면 AI는 효율을 위해 모든 변경을 한 번에 처리하려는 경향이 있다. 프로젝트의 "문서 먼저 → Step별 커밋" 원칙을 AI가 자발적으로 따르지 않았다.

**교훈**:
- AI에게 작업 프로세스(커밋 단위, 순서)를 명시적으로 지시해야 한다. "개선하자"만으로는 프로세스 원칙이 전달되지 않는다.
- 여러 Step이 있을 때는 "문서부터 커밋하고, 각 Step마다 커밋해"처럼 구체적으로 제어해야 한다.

#### AI의 과도한 개선 제안 수용

**문제 정의**: 코드 리뷰에서 `Order.calculateTotalPrice()`의 `int` 오버플로를 지적하고, `int` → `long` 전환 + Flyway 마이그레이션 + 관련 코드 전체 수정을 제안했다. 실제로 구현까지 진행한 뒤 사용자가 "포인트를 21억 이상 줄 경우가 존재할까?"로 반박하여 전체 되돌림.

**발생 원인**: AI가 코드 리뷰 시 이론적으로 가능한 문제를 모두 지적하고, 사용자가 "개선하자"고 하면 비용 대비 효과를 따지지 않고 모두 구현하려 한다. 현실적으로 발생할 수 없는 시나리오(단일 주문 640개 이상)에 대해 Member 엔티티, MemberService, Flyway 마이그레이션까지 수정하는 것은 과도했다.

**교훈**:
- AI의 개선 제안을 수용하기 전에 "이 문제가 실제로 발생할 가능성이 있는가?"를 먼저 판단해야 한다.
- 이론적 완벽함보다 실용적 판단이 중요하다. 비현실적 시나리오에 대한 방어 코드는 복잡성만 늘린다.
- AI에게 "이거 현실적으로 발생해?"라고 먼저 물어보는 습관이 필요하다.

#### 세션 전환 시 미커밋 변경 유실

**문제 정의**: 이전 세션의 컨텍스트가 1M 한도로 꽉 차서 새 세션에서 이어 작업했다. 새 세션에서 `git reset` + 재커밋을 시도했는데, 이전 세션에서 unstaged 상태로 남아 있던 Phase 6 변경 파일들(Repository, Controller, 테스트 등)을 누락한 채 커밋했다. 이후 `--force-with-lease`로 push하면서 리모트까지 불완전한 상태로 덮어써 컴파일 에러 14개가 발생했다.

**발생 원인**: 새 세션은 이전 세션의 git 상태(어떤 파일이 unstaged였는지, 어떤 변경이 커밋 대상이었는지)를 모른다. 이전 세션에서 변경사항을 커밋하지 않은 채로 세션이 종료되었고, 새 세션에서 컨텍스트 없이 git 조작을 하면서 파일을 유실했다.

**복구**: worktree(`claude/mystifying-sanderson-326170`)에 이전 세션의 작업이 온전히 남아있어서 복구할 수 있었다.

**교훈**:
- 세션 전환 전에 모든 변경사항을 커밋하거나 stash해야 한다. unstaged 상태로 남기면 새 세션에서 유실될 수 있다.
- 새 세션에서 이전 작업의 git 상태를 조작할 때는 특히 주의해야 한다. 컨텍스트가 없는 상태에서 `reset`, `amend`, `force-push`는 위험하다.
- worktree가 독립된 작업 사본 역할을 해서 복구 수단이 되었다. 없었으면 이전 세션 작업을 전부 다시 해야 했다.

각 Phase가 끝날 때마다 이 표에 항목을 추가한다.

---

## 자연어 기반 AI 개발 소감

이 과제는 코드를 직접 작성하지 않고, 자연어로 Claude Code에 명령하여 전체 리팩터링을 진행했다. 방향성과 의사결정은 사람이 하고, 구현은 AI가 수행하는 방식이다.

### 장점

- **구조 파악 속도**: 서비스 계층도 없고 컨트롤러에 로직이 몰린 레거시 코드를 빠르게 분석하고, 통합 테스트 37개를 생성하여 리팩터링 안전망을 단기간에 구축했다.
- **반복 작업 위임**: 6개 서비스 추출, 예외 계층 전환 등 패턴이 반복되는 작업을 일관성 있게 처리했다.
- **문서화 동시 진행**: ADR, 플랜 문서, 레거시 분석 문서를 코드 작업과 병행하여 의사결정 맥락을 실시간으로 기록했다.

### 문제점

- **관찰 가능성 누락**: AI에게 세부 기획을 지시했을 때, AI 입장에서는 기능을 구현하고 테스트만 통과하면 되기 때문에 사람이 유지보수할 때 필요한 관점이 반영되지 않았다. 기획을 너무 추상적으로 지시한 탓도 있어서, 로그 같은 운영 필수 요소가 자연스럽게 빠졌다. 실제로 Phase 4에서 카카오 메시지 발송을 수동 테스트하려 했을 때, 로그가 전혀 없어서 정상 동작 여부를 확인할 수 없었고, 급히 로깅 작업을 앞당겨 처리했다.
- **AI 테스트 통과 ≠ 정상 동작**: 테스트를 AI가 작성하면, 잘못된 전제를 테스트가 그대로 반영할 수 있다. 존재하지 않는 API를 `.http` 파일에 넣은 것처럼, AI가 "그럴듯하게 틀리는" 경우는 AI가 만든 테스트로는 잡히지 않는다.
- **결과물 의존**: 사람이었으면 로그를 찍어가며 매 단계를 확인했을 텐데, AI가 테스트 통과를 보고하면 그대로 넘어가게 된다. 생산성이 높았지만 그것은 "클로드 기반 생산성"이지 "사람 기반 생산성"이 아니었다.
- **개발자에서 관리자로**: 직접 개발할 때는 코드 한 줄마다 고민하고 고뇌했는데, AI에게 작업을 지시하고 결과를 확인·승인하는 방식이다 보니 작업 내용이 빠르게 스쳐가는 느낌이 있었다. 세부 계획도 내가 고민해서 짜는 게 아니라 남이 해준 걸 검토하는 관리자 역할에 가까웠고, 내부 코드의 세부 구조를 깊이 파악하지 못한 채 작동 확인이나 보안 점검까지 AI에게 맡기게 되었다.

### 교훈

1. **로그·모니터링은 개발 초기에 넣어야 한다** — AI가 필요 없어도 사람이 필요하다.
2. **수동 검증 단계를 프로세스에 포함시켜야 한다** — AI 테스트 통과만으로 넘어가지 않는다.
3. **AI 산출물은 비판적으로 검토해야 한다** — 환각은 방향성을 잡아줘도 발생한다.
4. **도구 숙련도를 높여야 한다** — skills, agent 등을 적극 활용하면 AI의 한계를 보완할 수 있다.
5. **세션 전환에 대비한 컨텍스트 관리가 핵심이다** — 세션이 바뀌면 AI의 기억은 0에서 시작한다. 현업에서는 1M 컨텍스트 한도로 세션 교체가 빈번한데, 이때 `CLAUDE.md`와 `.claude/skills/`가 매 세션마다 자동 로드되는 **영구 컨텍스트** 역할을 한다. 자연어로 매번 설명하는 비용보다 스킬 한 번 만드는 비용이 낮고, 세션이 자주 바뀔수록 이 격차는 커진다. 다른 스터디원(초이님: Ralph 워크플로, 개발인생님: 커스텀 스킬 4개)과 비교했을 때, 이 부분의 숙련도 차이가 AI 활용 품질의 차이로 직결되었다.
