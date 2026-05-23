# Phase 4 — 외부 API 연동 안정화 플랜

## 현황

외부 API 연동 지점은 2곳이다.

| 클라이언트 | 호출 시점 | 성격 |
|---|---|---|
| `KakaoLoginClient` | 카카오 로그인 (OAuth 토큰 교환 + 사용자 정보 조회) | 동기, 실패 시 로그인 불가 |
| `KakaoMessageClient` | 주문 완료 후 알림 발송 | 커밋 후 best-effort (ADR-0003) |

## 문제점

| # | 문제 | 위치 | 영향 |
|---|---|---|---|
| 1 | 타임아웃 미설정 — 카카오 무응답 시 스레드 무한 대기 | 두 Client 모두 | 서버 스레드 고갈 |
| 2 | 에러 핸들링 부재 — 카카오 4xx/5xx 시 그대로 500 전파 | KakaoLoginClient | 사용자에게 500 노출 |
| 3 | 에러 로깅 없음 — 발송 실패를 catch하고 무시 | OrderNotificationListener | 장애 원인 추적 불가 |
| 4 | 시크릿 보호 미흡 — `.env`, `application-local.properties`가 .gitignore에 없음 | .gitignore | 시크릿 유출 위험 |
| 5 | baseUrl 하드코딩 — 매 호출마다 전체 URL 직접 작성 | 두 Client 모두 | 변경 시 산재된 수정 |

## Step 구성

### Step 1 — 외부 API 호출 로깅 추가

**목표**: 카카오 API 호출 흐름을 로그로 추적할 수 있게 한다. 프로젝트 전체에 로그가 없어 디버깅이 불가능한 상태를 해소한다.

**변경 내용**:
- `KakaoLoginClient`: 토큰 교환 요청/성공, 사용자 조회 요청/성공 로그
- `KakaoMessageClient`: 메시지 발송 요청, API 응답 수신 로그
- `OrderNotificationListener`: 토큰 없음 생략(debug), 발송 성공(info), 발송 실패(warn + 예외)

**검증**: 구조 변경, 외부 작동 동일 (발송 실패 시 주문에 영향 없는 기존 동작 유지)

### Step 2 — 카카오 로그인 에러 핸들링

**목표**: 카카오 API 실패 시 클라이언트에는 일관된 메시지를, 내부에는 디버깅 가능한 로그를 남긴다.

**에러 처리 전략**:
- 클라이언트 응답: 401 + `"카카오 로그인에 실패했습니다"` (고정 메시지)
- 서버 로그: 카카오 에러 코드, HTTP 상태, 응답 본문을 `log.warn`으로 기록

**변경 내용**:
- `KakaoLoginClient`에서 `RestClientResponseException` catch → 로그 기록 → `AuthenticationException` 변환
- 연결 실패(`ResourceAccessException`) 별도 catch → `log.error` → 적절한 예외 전환

**검증**: 카카오 API 실패 시 500이 아닌 401 반환 테스트

### Step 3 — RestClient 타임아웃 설정

**목표**: 카카오 서버 무응답 시 스레드가 무한 대기하지 않도록 한다.

**변경 내용**:
- `RestClient.Builder`에 connect timeout(5초), read timeout(5초) 설정
- `KakaoLoginClient`, `KakaoMessageClient` 모두 적용

**검증**: 타임아웃 설정이 적용되었는지 빈 설정 확인 (구조 변경, 외부 작동 동일)

### ~~Step 4 — RestClient baseUrl 정리~~ (건너뜀)

**건너뛴 사유**: `RestClient.Builder.baseUrl()`로 추출해도 생성자에 URL이 하드코딩되는 것은 동일하다. `application.properties`로 외부화하면 해결되지만, 카카오 API URL은 변경될 일이 사실상 없어 실질적인 가치가 없다고 판단했다.

### Step 5 — .gitignore 시크릿 보호 + 환경변수 가이드

**목표**: 시크릿 파일이 실수로 커밋되지 않도록 한다.

**변경 내용**:
- `.gitignore`에 `.env`, `application-local.properties`, `application-prod.properties` 추가
- README 환경변수 섹션에 로컬 실행 가이드 보강

**검증**: `git status`에서 시크릿 파일이 추적되지 않는지 확인

## 고려했으나 제외한 것

| 패턴 | 제외 사유 |
|---|---|
| **Outbox 패턴** | 카카오 알림은 best-effort. ADR-0003에서 결정 완료. 별도 테이블 + 스케줄러는 현재 규모에 과도 |
| **재시도(Retry)** | 메시지 발송은 실패해도 주문 무관. 로그인은 사용자 재시도 가능. 자동 retry 시 카카오 rate limit 초과 위험 |
| **Circuit Breaker** | 외부 API 호출 빈도가 낮아 서킷 상태 관리 오버헤드가 이점보다 큼 |
| **비동기 발송(@Async)** | ADR-0003에서 제외. 스레드 풀 관리 추가됨 |
| **카카오 에러 코드별 세분화** | KOE101/010/303은 서버 설정 오류로 배포 후 거의 발생 안 함. 로그로 구분하되 클라이언트 응답은 단일 메시지로 통일 |
