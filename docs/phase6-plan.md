# Phase 6 — 1차 코드 리뷰 반영 플랜

## 배경

PR #8에 대한 리뷰 코멘트 9개 중 코드 수정이 필요한 5개 항목을 개선한다.
추가로 로깅 횡단 처리(AOP)를 도입하여 반복되는 로그 코드를 제거한다.

## Step 구성

### Step 1 — @Transactional 적용 + 불필요한 save() 제거 [리뷰 #2]

**목표**: 서비스에 `@Transactional`을 적용하여 더티 체킹을 활성화하고, 불필요한 명시적 `save()` 호출을 제거한다.

**변경 내용**:
- 전 서비스에 클래스 레벨 `@Transactional(readOnly = true)` 적용
- 쓰기 메서드에 `@Transactional` 오버라이드
- 기존 엔티티 수정 시 `save()` 제거 (더티 체킹으로 자동 반영)
- 신규 엔티티 생성 시 `save()` 유지 (아직 영속화 안 됐으므로)

**대상 서비스**: MemberService, CategoryService, ProductService, OptionService, WishService, OrderService

**검증**: 기존 테스트 전체 통과 확인

### Step 2 — 카테고리/상품 삭제 시 연결 엔티티 확인 [리뷰 #3]

**목표**: 연결된 엔티티가 있는 카테고리/상품 삭제 시 FK 제약으로 500이 반환되는 문제를 해소한다.

**변경 내용**:
- `CategoryService.removeCategory()`: `ProductRepository.existsByCategoryId(id)` 체크, 연결된 상품 있으면 400 반환
- `ProductService.removeProduct()`: 연결된 위시/주문 존재 여부 체크, 있으면 400 반환
- `ProductRepository`에 `existsByCategoryId()` 메서드 추가
- `WishRepository`에 `existsByProductId()` 메서드 추가 (이미 존재 시 생략)
- `OrderRepository`에 상품 연결 확인 메서드 추가

**검증**: 기존 테스트 전체 통과 + 연결된 엔티티 삭제 시 400 확인

### Step 3 — Service 파라미터에서 Request DTO 제거 [리뷰 #5]

**목표**: Service가 Controller의 DTO를 모르도록 하여, 계층 간 의존성을 정리한다.

**변경 내용**:
- `CategoryService`: `addCategory(CategoryRequest)` → `addCategory(String name, String color, String imageUrl, String description)`, `updateCategory` 동일
- `ProductService`: `addProduct(ProductRequest)` → 개별 파라미터, `updateProduct` 동일
- `OptionService`: `addOption(Long productId, OptionRequest)` → 개별 파라미터
- Controller에서 DTO를 분해하여 Service에 전달

**검증**: 구조 변경, 기존 테스트 전체 통과 확인

### Step 4 — 의미없는 INFO 로그 제거 [리뷰 #6]

**목표**: KakaoLoginClient, KakaoMessageClient에서 요청/응답을 중복으로 찍고 있는 불필요한 INFO 로그를 제거한다.

**변경 내용**:
- `KakaoLoginClient`: "카카오 토큰 교환 요청", "카카오 토큰 교환 성공", "카카오 사용자 정보 조회 요청", "카카오 사용자 정보 조회 성공" 제거
- `KakaoMessageClient`: "카카오 메시지 발송 요청", "카카오 API 응답 수신 완료" 제거
- `log.warn` (실패 로그)은 유지

**검증**: 구조 변경, 외부 작동 동일

### ~~Step 5 — 로깅 횡단 처리 (AOP 도입)~~ (생략)

**생략 사유**: 프로젝트 규모에서 AOP 도입은 과도하다. info/warn/error 구분이 비즈니스 맥락에 따라 다르고, 반환값 정보(id, name)가 유실된다. 리뷰어의 주요 지적은 불필요한 INFO 로그였으며, Step 4에서 해소 완료.

## 추가 개선사항 (코드 리뷰 발견)

| # | 심각도 | 파일 | 내용 |
|---|---|---|---|
| 1 | 🔴 버그 | `LoginMemberArgumentResolver:32` | Authorization 헤더 null 시 NPE — null 체크 추가 필요 |
| 2 | 🟡 일관성 | `OptionController:31` | DTO 변환 Service 통일에서 누락 — `getProductOptions`가 엔티티 반환 |
| 3 | 🟡 일관성 | `AdminProductController:33` | SSR에 `CategoryResponse` 혼재 — SSR용 DTO 필요 여부 검토 |
| 4 | 🟢 죽은 코드 | `ProductService:43` | `getAllProducts()` (no pageable) 호출자 없음 — 제거 |
| 5 | 🟢 중복 | `OrderService:55` | `calculateTotalPrice()` 두 번 호출 — 로컬 변수로 통합 |
| 6 | 🟢 죽은 코드 | `ProductService:53` | `existsByCategoryId()` 호출자 없음 — 제거 |

### Step 7 — DB에서 확인 가능한 CRUD 성공 로그 제거 [2차 리뷰]

**목표**: DB 조회로 확인 가능한 CRUD 성공 로그(log.info)를 제거하여 운영 시 노이즈를 줄인다. 보안 감사, 비즈니스 핵심 이벤트, 외부 API 호출 결과 로그는 유지한다.

**삭제 대상 (14개)**:
- `CategoryService`: 카테고리 추가/수정/삭제
- `ProductService`: 상품 추가(2곳)/수정(2곳)/삭제
- `OptionService`: 옵션 추가/삭제, 재고 차감
- `WishService`: 위시 추가/삭제
- `MemberService`: 회원가입 성공

**유지 대상 (4개)**:
- `AuthService`: 로그인 성공 (보안 감사)
- `KakaoAuthService`: 카카오 로그인 성공 (보안 감사)
- `OrderService`: 주문 생성 (비즈니스 핵심 이벤트, 금액 포함)
- `OrderNotificationListener`: 카카오 메시지 발송 성공 (외부 API 호출 결과)

**검증**: 기존 테스트 전체 통과 확인

### Step 6 — 테스트 코드 var → 명시적 타입 변경 [리뷰 #8]

**목표**: 테스트 코드에서 `var` 사용을 명시적 타입으로 변경하여 프로젝트 코드 스타일과 통일한다.

**변경 내용**:
- `OrderTransactionTest`의 `var` → `String`, `OrderRequest` 등 명시적 타입으로 변경
- 기타 테스트 파일에 `var` 사용 여부 확인 후 일괄 변경

**검증**: 구조 변경, 기존 테스트 전체 통과 확인
