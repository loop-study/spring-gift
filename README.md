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

### Phase 4 — 환경 및 문서 정리 (선택)

- [ ] 카카오 API 환경변수 가이드 정리 (application-local.properties 또는 .env)
- [ ] `.gitignore` 시크릿 보호 확인
- [ ] ADR 추가 작성 (트랜잭션 경계, 카카오 메시지 발송 시점, 테스트 전략)

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

### 로그

| 일자 | 단계 | 활용한 방식 | 학습/판단 |
|---|---|---|---|
| 2026-05-11 | 탐색 | 전체 소스 파일 일괄 읽기 → 엔드포인트/도메인/문제점 표 정리 | OrderController에 트랜잭션 경계 없음, wish cleanup 누락, 서비스 계층 부재를 체계적으로 발견. legacy-info.md로 분석 결과 문서화. |
| 2026-05-11 | 기획 | legacy-info.md 기반으로 Phase별 체크리스트 도출, 문제 번호와 커밋 단위를 1:1 매핑 | 구조 변경이 작동 변경의 전제임을 확인 — Phase 순서 의존성 확립. |
| 2026-05-13 | 구현 | 도메인 의존성 순서대로 통합 테스트 작성 (Category→Member→Product→Option→Wish→Order, 총 37개 테스트). IntegrationTest 추상 클래스 추출로 Spring 컨텍스트 캐싱 및 공통 헬퍼 재사용. | 테스트 작성 순서를 의존성 기반으로 정하니 시드 데이터 설계가 자연스럽게 정리됨. @Transactional 롤백 전략으로 테스트 간 격리 확보. |
| 2026-05-15 | 구현 | Plan 에이전트로 서비스 추출 플랜 수립 후 도메인별 1커밋씩 6개 서비스 추출. 매 커밋마다 37개 통합 테스트 전체 통과 확인. Explore 에이전트로 추출 전후 작동 변경 여부 교차 검증. | `@RestControllerAdvice`를 구조 변경으로 분류했다가, 전역 적용 시 기존 500 응답이 400으로 바뀌는 점을 발견하여 Phase 3(작동 변경)으로 재분류. 분류 기준을 "코드 위치"가 아닌 "사용자가 관찰 가능한 결과"로 재정립. |
| 2026-05-17 | 구현 | Explore 에이전트로 전체 예외 사용 현황 분석 → legacy-exception-info.md 문서화. Plan 에이전트로 Step 1을 7개 sub-step으로 세분화. Red-Green 패턴으로 10커밋에 걸쳐 글로벌 예외 핸들러 + 커스텀 예외 계층 도입. | 중복 옵션명도 DuplicateEntityException으로 교체 시 400→409 변경이 발생함을 테스트 실행 중 발견. 플랜에서 누락된 작동 변경을 테스트가 잡아줌 — Red-Green 패턴의 안전망 역할 확인. |

### 실패 사례와 교훈

#### WishService 추출 시 불필요한 설계 변경 혼입

**문제 정의**: Phase 2는 "코드를 그대로 옮기기만 한다"가 원칙이다. 그러나 AI가 WishService를 추출하면서 원본에 없던 `AddResult` record와 `RemoveResult` enum을 새로 도입했다. 다른 5개 서비스는 null 반환, boolean 반환 등 원본 패턴을 유지했는데, WishService만 새로운 타입 설계가 섞였다.

**발생 원인**: AI에게 서비스 추출을 일괄 요청하면서, 결과물의 패턴 일관성을 커밋 전에 검토하지 않았다. 테스트가 통과한다는 사실에 안심하여 "외부 작동이 같으면 된다"고 넘어갔지만, 한 커밋에 **추출 + 설계 변경**이 섞인 것은 프로젝트의 커밋 원칙 위반이다.

**교훈**:
- 테스트 통과 ≠ 원칙 준수. 테스트는 외부 작동만 검증하고, 커밋 원칙(구조/작동 분리)은 사람이 직접 확인해야 한다.
- AI 산출물을 승인할 때 "다른 서비스와 패턴이 동일한가?"를 체크리스트에 넣어야 한다.
- 새로운 타입(enum, record) 도입은 설계 결정이므로, 단순 추출 커밋과 분리해야 한다.

각 Phase가 끝날 때마다 이 표에 항목을 추가한다.
