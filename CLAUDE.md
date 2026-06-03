# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew build         # 컴파일 + 테스트
./gradlew test          # 테스트만
./gradlew bootRun       # 로컬 실행 (H2 인메모리)
./gradlew test --tests "gift.order.OrderControllerTest"  # 단일 테스트 클래스
./gradlew test --tests "gift.order.OrderControllerTest.주문_목록을_조회한다"  # 단일 테스트 메서드
```

## Architecture

Spring Boot 3.5.9 + Java 21 선물하기 커머스 플랫폼. 모든 프로덕션 코드는 `src/main/java/gift/` 하위에 Java로 작성.

### Package Structure

도메인별 패키지. 각 패키지는 Entity, Controller, Service, Repository, Request/Response DTO로 구성.

- `auth/` — JWT 인증 + 카카오 OAuth. `AuthService`(로그인), `KakaoAuthService`(카카오), `AuthenticationResolver` + `@LoginMember` ArgumentResolver
- `member/` — 회원 CRUD + 포인트. `MemberService`는 인증 로직 없이 회원 도메인만 담당
- `category/` — 카테고리 CRUD
- `product/` — 상품 CRUD. `AdminProductController`(SSR)와 `ProductController`(REST) 분리
- `option/` — 상품 옵션. 재고 차감 로직 포함
- `wish/` — 위시리스트. `(member_id, product_id)` unique constraint
- `order/` — 주문. `@Transactional`로 원자적 처리. 카카오 알림은 `@TransactionalEventListener(AFTER_COMMIT)`
- `exception/` — `BusinessException` 루트의 커스텀 예외 계층 + `GlobalExceptionHandler`
- `config/` — `SecurityConfig`(BCrypt Bean), `WebConfig`(ArgumentResolver), `RestClientConfig`(타임아웃)

### Key Design Decisions

- **인증**: JWT 기반. `@LoginMember` 어노테이션으로 컨트롤러에서 인증된 Member를 주입받음
- **비밀번호**: BCrypt 해싱. `spring-security-crypto` 사용 (`spring-boot-starter-security` 미사용)
- **예외**: `GlobalExceptionHandler`가 예외 타입별로 HTTP 상태 코드 매핑. 새 예외 추가 시 핸들러도 추가 필요
- **DTO 변환**: Service에서 Response DTO 반환. Controller는 위임만
- **SSR**: Admin 페이지는 Thymeleaf SSR. `AdminProductResponse` 등 별도 DTO 사용
- **트랜잭션**: 클래스 레벨 `@Transactional(readOnly = true)` + 쓰기 메서드만 `@Transactional` 오버라이드
- **도메인 간 참조**: Service → Service 호출. 다른 도메인의 Repository 직접 참조 금지 (순환 의존 방지를 위한 예외: CategoryService → ProductRepository)

### Database

- H2 인메모리 (개발/테스트), Flyway 마이그레이션 (`src/main/resources/db/migration/V*.sql`)
- 시드 데이터: `V2__Insert_default_data.sql` — 카테고리 3개, 상품 3개, 회원 2개, 위시 2개, 옵션 3개, 주문 2개

### Testing

- JUnit 5 통합 테스트. `IntegrationTest` 추상 클래스 상속 (Spring 컨텍스트 캐싱 + `loginAndGetToken` 헬퍼)
- 테스트 메서드명은 한국어 (`void 주문_목록을_조회한다()`)
- `@Transactional` 롤백으로 테스트 격리. `OrderTransactionTest`만 `Propagation.NOT_SUPPORTED`

## Commit Convention

AngularJS 스타일: `type(scope): subject`
- `feat`, `fix`, `refactor`, `docs`, `test`, `style`, `chore`
- 구조 변경과 작동 변경을 같은 커밋에 섞지 않는다
- 작동 변경은 테스트로 증거를 남긴다

## Process Rules

- 코드 수정 전 plan 문서(`docs/phaseN-plan.md`)에 작업 항목을 먼저 정의한다
- 문서 변경과 코드 변경을 같은 커밋에 섞지 않는다
- Step별로 커밋한다 — 여러 Step을 한 번에 진행하지 않는다
- 모든 코드 변경 후 `./gradlew test` 전체 통과를 확인한다
- 서버를 직접 실행하지 않는다 — 테스트로 검증한다
