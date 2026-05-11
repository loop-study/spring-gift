# Legacy Code Analysis — spring-gift

> README.md 작성 시 참고용. 현재 코드베이스의 구조, API 표면, E2E 흐름, 발견된 문제점을 정리한다.

## 1. 도메인 모델

```
Category (1) ──< Product (1) ──< Option
                    │                │
                    │                │
                    ▼                ▼
                  Wish            Order ──> memberId (primitive FK)
                    │
                    └──> memberId (primitive FK)
```

| 엔티티 | 필드 | 도메인 행위 |
|---|---|---|
| Category | id, name, color, imageUrl, description | update(...) |
| Product | id, name, price, imageUrl, category, options(cascade ALL) | update(...) |
| Option | id, product, name, quantity | subtractQuantity(amount) — 재고 부족 시 IAE |
| Member | id, email, password, kakaoAccessToken, point | chargePoint(amount), deductPoint(amount) — 부족 시 IAE |
| Wish | id, memberId(primitive FK), product | 행위 없음 |
| Order | id, option, memberId(primitive FK), quantity, message, orderDateTime | 행위 없음 |

### 검증 규칙

- **ProductNameValidator**: 최대 15자, 허용 문자만, "카카오" 포함 시 거부 (Admin은 allowKakao=true로 허용)
- **OptionNameValidator**: 최대 50자, 허용 문자만
- **OptionRequest**: quantity는 1 ~ 99,999,999
- **MemberRequest**: @Email, @NotBlank
- **ProductRequest**: @NotBlank name, @Positive price, @NotBlank imageUrl, @NotNull categoryId

## 2. REST API 엔드포인트

| 메서드 | 경로 | 인증 | 하는 일 | 컨트롤러 |
|---|---|---|---|---|
| POST | /api/members/register | X | 회원가입 → JWT 발급 | MemberController |
| POST | /api/members/login | X | 이메일/PW 로그인 → JWT | MemberController |
| GET | /api/auth/kakao/login | X | 카카오 OAuth 페이지로 redirect | KakaoAuthController |
| GET | /api/auth/kakao/callback | X | 인가코드 교환 → 자동가입 → JWT | KakaoAuthController |
| GET | /api/products | X | 상품 목록 (페이지네이션) | ProductController |
| GET | /api/products/{id} | X | 상품 단건 조회 | ProductController |
| POST | /api/products | X | 상품 생성 (이름 검증) | ProductController |
| PUT | /api/products/{id} | X | 상품 수정 | ProductController |
| DELETE | /api/products/{id} | X | 상품 삭제 | ProductController |
| GET | /api/products/{pid}/options | X | 옵션 목록 | OptionController |
| POST | /api/products/{pid}/options | X | 옵션 생성 (이름 검증, 중복 체크) | OptionController |
| DELETE | /api/products/{pid}/options/{oid} | X | 옵션 삭제 (최소 1개 규칙) | OptionController |
| GET | /api/categories | X | 카테고리 전체 조회 | CategoryController |
| POST | /api/categories | X | 카테고리 생성 | CategoryController |
| PUT | /api/categories/{id} | X | 카테고리 수정 | CategoryController |
| DELETE | /api/categories/{id} | X | 카테고리 삭제 | CategoryController |
| GET | /api/wishes | O | 위시리스트 조회 (페이지네이션) | WishController |
| POST | /api/wishes | O | 위시 추가 (중복 방지) | WishController |
| DELETE | /api/wishes/{id} | O | 위시 삭제 (소유자 검증) | WishController |
| GET | /api/orders | O | 주문 목록 (페이지네이션) | OrderController |
| POST | /api/orders | O | 주문 생성 (핵심 흐름) | OrderController |

## 3. Admin SSR 엔드포인트 (Thymeleaf)

| 메서드 | 경로 | 하는 일 | 컨트롤러 |
|---|---|---|---|
| GET | /admin/products | 상품 목록 페이지 | AdminProductController |
| GET | /admin/products/new | 상품 생성 폼 | AdminProductController |
| POST | /admin/products | 상품 생성 처리 | AdminProductController |
| GET | /admin/products/{id}/edit | 상품 수정 폼 | AdminProductController |
| POST | /admin/products/{id}/edit | 상품 수정 처리 | AdminProductController |
| POST | /admin/products/{id}/delete | 상품 삭제 | AdminProductController |
| GET | /admin/members | 회원 목록 페이지 | AdminMemberController |
| GET | /admin/members/new | 회원 생성 폼 | AdminMemberController |
| POST | /admin/members | 회원 생성 처리 | AdminMemberController |
| GET | /admin/members/{id}/edit | 회원 수정 폼 | AdminMemberController |
| POST | /admin/members/{id}/edit | 회원 수정 처리 | AdminMemberController |
| POST | /admin/members/{id}/charge-point | 포인트 충전 | AdminMemberController |
| POST | /admin/members/{id}/delete | 회원 삭제 | AdminMemberController |

## 4. 핵심 E2E 흐름 — 주문 생성

```
POST /api/orders { optionId, quantity, message }
  │
  ├─ 1. 인증: Authorization 헤더 → JWT → Member 조회
  │     → 실패 시 401
  │
  ├─ 2. 옵션 검증: optionId로 Option 조회
  │     → 없으면 404
  │
  ├─ 3. 재고 차감: option.subtractQuantity(quantity)
  │     optionRepository.save(option)        ← DB 즉시 반영
  │
  ├─ 4. 포인트 차감: price = option.product.price * quantity  ← 컨트롤러에서 계산
  │     member.deductPoint(price)
  │     memberRepository.save(member)        ← DB 즉시 반영
  │
  ├─ 5. 주문 저장: new Order(...) → orderRepository.save
  │
  ├─ 6. [누락] wish cleanup ← 주석만 있고 코드 없음
  │
  └─ 7. 카카오 메시지: try-catch로 감싸 best-effort
        → 실패해도 무시
```

## 5. 인증 흐름

- JWT 기반 (jjwt 라이브러리)
- JwtProvider: email을 subject로 토큰 생성/검증
- AuthenticationResolver: Authorization 헤더에서 "Bearer " 제거 → JWT 파싱 → email로 Member 조회
  - 어떤 예외든 발생하면 null 반환 (만료, 잘못된 형식, 미존재 구분 없음)
- 로그인 경로 2가지: 이메일/PW 직접 로그인, 카카오 OAuth

## 6. 발견된 문제점

### 아키텍처

| # | 문제 | 위치 | 심각도 |
|---|---|---|---|
| A1 | 서비스 계층 없음 — 모든 비즈니스 로직이 컨트롤러에 직결 | 전체 | 높음 |
| A2 | 테스트 0건 — test 디렉터리에 .gitkeep만 존재 | src/test/ | 높음 |
| A3 | @Transactional 없음 — 다중 쓰기가 원자적이지 않음 | OrderController:87-96 | 높음 |

### 작동 결함

| # | 문제 | 증상 |
|---|---|---|
| B1 | 주문 시 데이터 부정합 가능 — 재고 차감 후 포인트 차감 예외 시 재고만 깎이고 주문 안 됨 | 재고 누수 |
| B2 | wish cleanup 미구현 — 주석(line 67)에 "6. cleanup wish" 있지만 코드 없음 | 주문 후에도 위시에 상품 남음 |
| B3 | 가격 계산이 컨트롤러에 — option.getProduct().getPrice() * request.quantity() (line 91) | 도메인 책임 유출 |

### 코드 품질

| # | 문제 | 위치 |
|---|---|---|
| C1 | @ExceptionHandler 3곳에 산재 | MemberController:57, ProductController:97, OptionController:100 |
| C2 | 의미 없는 javadoc (@author brian.kim @since 1.0) | Member, MemberRequest, MemberRepository, JwtProvider, TokenResponse, AdminMemberController |
| C3 | 인증 패턴 중복 — if (member == null) return 401 이 4곳 반복 | OrderController, WishController 각 메서드 |
| C4 | 비밀번호 평문 저장 | Member.password, MemberController.login |
| C5 | Admin은 상품명에 "카카오" 허용(allowKakao=true), API는 불허 — 규칙이 코드에만 존재 | AdminProductController:47 vs ProductController:90 |

### 도메인 모델 참고

| # | 관찰 |
|---|---|
| D1 | Wish/Order가 memberId(primitive FK) 사용, Member 엔티티 참조 안 함 — Order는 Option 엔티티 참조는 함 (비일관) |
| D2 | Product → Option: CascadeType.ALL + orphanRemoval — 상품 삭제 시 옵션 자동 삭제 |
| D3 | Product → Wish: cascade 없음 — 위시 있는 상품 삭제 시 FK 위반 가능 |

## 7. 파일 구조

```
src/main/java/gift/
├── Application.java
├── auth/
│   ├── AuthenticationResolver.java
│   ├── JwtProvider.java
│   ├── KakaoAuthController.java
│   ├── KakaoLoginClient.java
│   ├── KakaoLoginProperties.java
│   └── TokenResponse.java
├── category/
│   ├── Category.java
│   ├── CategoryController.java
│   ├── CategoryRepository.java
│   ├── CategoryRequest.java
│   └── CategoryResponse.java
├── member/
│   ├── AdminMemberController.java
│   ├── Member.java
│   ├── MemberController.java
│   ├── MemberRepository.java
│   └── MemberRequest.java
├── option/
│   ├── Option.java
│   ├── OptionController.java
│   ├── OptionNameValidator.java
│   ├── OptionRepository.java
│   ├── OptionRequest.java
│   └── OptionResponse.java
├── order/
│   ├── KakaoMessageClient.java
│   ├── Order.java
│   ├── OrderController.java
│   ├── OrderRepository.java
│   ├── OrderRequest.java
│   └── OrderResponse.java
├── product/
│   ├── AdminProductController.java
│   ├── Product.java
│   ├── ProductController.java
│   ├── ProductNameValidator.java
│   ├── ProductRepository.java
│   ├── ProductRequest.java
│   └── ProductResponse.java
└── wish/
    ├── Wish.java
    ├── WishController.java
    ├── WishRepository.java
    ├── WishRequest.java
    └── WishResponse.java
```

## 8. 기술 스택

- Java 21, Kotlin 1.9 (빌드에 Kotlin 플러그인 있으나 실제 Kotlin 코드 없음)
- Spring Boot 3.5.9
- Spring Data JPA + Flyway
- H2 (런타임) + MySQL Connector (런타임)
- jjwt 0.13.0
- Thymeleaf (Admin SSR)
- Bean Validation (jakarta.validation)
- ktlint (코드 스타일)
