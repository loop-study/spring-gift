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

- [ ] `application-test.properties` 작성 (H2 in-memory + Flyway)
- [ ] Application 컨텍스트 로딩 스모크 테스트
- [ ] 주요 API 엔드포인트별 통합 테스트 기반 마련 (상품 CRUD 기준)

### Phase 2 — 구조 변경 (작동 유지)

> 목표: 서비스 계층을 추출하고, 중복 코드를 정리한다. 기존 작동은 변경하지 않는다. [A1, C1, C2, C3]

**서비스 계층 추출** — 각 도메인별 1커밋

- [ ] `OrderService` 추출 — OrderController의 주문 생성/조회 로직 이전
- [ ] `WishService` 추출 — WishController의 위시 추가/삭제/조회 로직 이전
- [ ] `MemberService` 추출 — MemberController 회원가입/로그인 + KakaoAuthController 카카오 로그인 로직 이전
- [ ] `ProductService` 추출 — ProductController + AdminProductController 공통 로직 이전
- [ ] `OptionService` 추출 — OptionController의 옵션 CRUD 로직 이전
- [ ] `CategoryService` 추출 — CategoryController의 카테고리 CRUD 로직 이전

**코드 정리** — 각 항목 1커밋

- [ ] `@RestControllerAdvice` 글로벌 예외 핸들러 도입 — 3곳의 `@ExceptionHandler` 통합 [C1]
- [ ] 의미 없는 javadoc 제거 (`@author brian.kim @since 1.0` 등) [C2]

### Phase 3 — 작동 변경 (증거로 검증)

> 목표: 누락된 작동을 구현하고, 데이터 정합성을 보장한다. 모든 변경은 테스트로 증명한다. [A3, B1, B2, B3]

- [ ] `@Transactional` 도입 — 주문 생성 시 재고 차감/포인트 차감/주문 저장을 하나의 트랜잭션으로 [A3, B1]
  - 검증: 포인트 부족 시 재고가 원래대로 롤백되는지 통합 테스트
- [ ] wish cleanup 구현 — 주문한 상품이 위시리스트에 있으면 제거 [B2]
  - 검증: 주문 생성 후 해당 상품의 위시가 삭제되었는지 통합 테스트
- [ ] 가격 계산 도메인 이전 — 총액 계산을 컨트롤러가 아닌 도메인이 담당 [B3]
  - 검증: 단위 테스트로 계산 결과 검증
- [ ] 카카오 메시지 발송 트랜잭션 분리 — 커밋 후 발송으로 변경
  - 검증: 트랜잭션 롤백 시 메시지가 발송되지 않는지 테스트

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

각 Phase가 끝날 때마다 이 표에 항목을 추가한다.
