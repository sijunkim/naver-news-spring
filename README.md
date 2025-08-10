

# PRD: 네이버 뉴스(속보/단독) 수집 → Slack 전송 (Spring WebFlux + Kotlin Coroutines + R2DBC)

## 0. TL;DR
네이버 뉴스에서 **속보/단독**을 주기적으로 수집하고(기본 60초), 필터링/중복 방지 후 **Slack Webhook**으로 전송하는 비동기 파이프라인입니다. 이전 NestJS 프로젝트의 파일 기반 상태 관리(텍스트 파일)를 **R2DBC 기반 DB 영속화**로 전환합니다. 실행 환경 변수는 기존 `.env`를 재사용하되, `APP_PORT`는 스프링에서 **`SERVER_PORT`**로 매핑하여 사용합니다.

---

## 1. 배경 및 목표
- **배경:** 기존 NestJS 기반 구현은 텍스트 파일로 상태(중복 키워드, 제외 키워드/언론사, 마지막 전송 시각)를 관리했습니다. 스케일/복구성/운영가시성 측면에서 한계가 있어 DB 기반으로 고도화합니다.
- **목표:**
  1) 완전 비동기 코루틴(Spring WebFlux + Kotlin)로 수집/전송 파이프라인 구성
  2) **DB(R2DBC)** 로 상태/로그 영속화 (파일 의존성 제거)
  3) **중복 방지**(URL 정규화 + 해시), **제외 룰**(키워드/언론사) 적용
  4) **재시도/백오프/레이트리밋** 대응 및 **관찰성(로그/메트릭/헬스체크)** 강화
  5) 운영/개발 환경에서 `.env` 재사용 (단, `APP_PORT`→`SERVER_PORT` 변경)

### 비범위(Non-Goals)
- 고급 NLP 기반의 유사도 판단(향후 확장으로 고려)
- Slack 스레딩/버튼 액션 등 고급 메시지 인터랙션(필요 시 확장)

---

## 2. 핵심 사용자 시나리오
- **운영자**로서, 실시간에 가까운 주기로 속보/단독을 Slack 채널에서 확인하고 싶다.
- **운영자**로서, 특정 키워드나 특정 언론사의 기사는 제외되길 원한다.
- **운영자**로서, 동일 기사가 중복 전송되지 않길 원한다.
- **운영자/개발자**로서, 장애 시 재시도/백오프로 자동 복구되길 원한다.
- **개발자**로서, 상태/전송 이력을 DB로 확인하고, 메트릭/헬스 체크를 통해 가시성을 확보하고 싶다.

---

## 3. 전체 흐름(High-Level)
1) **수집기(Fetcher)**: 60초 주기로 네이버 Open API 호출(속보/단독 각각)
2) **정규화/중복 방지**: 링크 URL 정규화 후 SHA-256 해시 생성 → DB에서 존재 여부 확인
3) **필터링**: 제외 키워드/언론사 룰에 일치하면 드롭
4) **저장**: 신규 기사라면 `news_article`에 저장
5) **전송**: Slack Webhook으로 비동기 전송, 결과를 `delivery_log`에 기록
6) **재시도/레이트리밋**: 429/5xx는 지수 백오프 + 지터로 재시도, 한계 초과 시 실패 기록

---

## 4. 아키텍처 구성요소
- **Spring Boot 3 + WebFlux + Kotlin Coroutines**: 논블로킹 비동기 처리
- **R2DBC(MySQL)**: 논블로킹 DB I/O
- **WebClient**: 네이버/Slack 외부 HTTP 호출
- **Scheduler(@Scheduled)**: 폴링 트리거 (각 채널 병렬 실행)
- **Config(@ConfigurationProperties)**: `.env` 값 바인딩 및 주입
- **Resilience(추가 예정)**: 필요 시 Resilience4j로 서킷브레이커/리트라이 적용
- **Actuator**: 헬스/메트릭 노출

---

## 5. 데이터 모델(초안)
```sql
CREATE TABLE news_article (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  naver_link_hash CHAR(64) NOT NULL UNIQUE,
  title VARCHAR(500) NOT NULL,
  summary TEXT NULL,
  press VARCHAR(100) NULL,
  published_at DATETIME NULL,
  fetched_at DATETIME NOT NULL,
  raw_json JSON NULL,
  INDEX idx_published_at (published_at DESC)
);

CREATE TABLE delivery_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  article_id BIGINT NOT NULL,
  channel ENUM('BREAKING','EXCLUSIVE','DEV') NOT NULL,
  status ENUM('SUCCESS','RETRY','FAILED') NOT NULL,
  http_status INT NULL,
  sent_at DATETIME NOT NULL,
  response_body TEXT NULL,
  UNIQUE KEY uniq_article_channel (article_id, channel),
  INDEX idx_sent_at (sent_at DESC)
);

CREATE TABLE keyword_exclusion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scope ENUM('BREAKING','EXCLUSIVE','ALL') NOT NULL,
  keyword VARCHAR(200) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_scope_keyword (scope, keyword)
);

CREATE TABLE press_exclusion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  press_name VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_press (press_name)
);

CREATE TABLE runtime_state (
  `key` VARCHAR(100) PRIMARY KEY,
  `value` TEXT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```
- **중복 방지 키**: `naver_link_hash`(정규화 URL의 SHA-256)
- **전송 중복 방지**: `delivery_log`의 `(article_id, channel)` UNIQUE
- **상태 이관**: 파일 기반의 마지막 수집시각/중복 키워드 카운트 등 → `runtime_state`로 이관

---

## 6. 중복 방지 & 제외 룰
- **URL 정규화**: 쿼리/프래그먼트 제거 + 소문자화 → SHA-256 해시
- **키워드/언론사 제외**: `keyword_exclusion`, `press_exclusion` 테이블 기반
- **키워드 중복 카운트**: (옵션) 최근 N분 창에서 유사 제목 카운트로 전송 여부 제한(단계2에서 고도화)

---

## 7. 스케줄링, 처리량, 레이트리밋
- **주기**: 기본 60초(`app.poll.intervalSeconds`), 채널별 병렬 실행(BREAKING/EXCLUSIVE)
- **레이트리밋**: Slack 429 응답 시 지수 백오프 + 지터로 재시도, 최대 횟수 초과 시 실패 기록
- **백프레셔**: 기사 수가 급증할 경우 배치 전송(확장 옵션) 또는 전송 큐(코루틴 채널) 적용

---

## 8. 외부 연동
### 8.1 네이버 Open API
- 엔드포인트: `.env`의 `NAVER_OPENAPI_URL` (예: `/v1/search/news.json`)
- 인증 헤더: `X-Naver-Client-Id`, `X-Naver-Client-Secret`
- 쿼리: `query=속보` / `query=단독` 등

### 8.2 Slack Webhook
- 채널별 Webhook URL: `BREAKING_NEWS_WEBHOOK_URL`, `EXCLUSIVE_NEWS_WEBHOOK_URL`, `DEVELOP_WEBHOOK_URL`
- 전송 페이로드: `{ "text": "[CHANNEL] 제목\n정규화URL" }`

---

## 9. 설정 & 환경변수 (.env 매핑)
- 기존 `.env` 재사용. 단, `APP_PORT`는 스프링에서 **`SERVER_PORT`**로 사용 (`server.port`).
- 주요 키 (예시):
  - `SERVER_PORT`(=기존 APP_PORT), `R2DBC_URL`, `R2DBC_USERNAME`, `R2DBC_PASSWORD`
  - `NAVER_OPENAPI_URL`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`
  - `BREAKING_NEWS_WEBHOOK_URL`, `EXCLUSIVE_NEWS_WEBHOOK_URL`, `DEVELOP_WEBHOOK_URL`
  - `POLL_INTERVAL_SECONDS`, `DUPLICATED_COUNT`

`application.yml` 예시:
```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  r2dbc:
    url: ${R2DBC_URL:r2dbc:pool:mysql://localhost:3306/news}
    username: ${R2DBC_USERNAME:root}
    password: ${R2DBC_PASSWORD:root}

app:
  poll:
    intervalSeconds: ${POLL_INTERVAL_SECONDS:60}
  duplicate:
    threshold: ${DUPLICATED_COUNT:5}

naver:
  openapi:
    url: ${NAVER_OPENAPI_URL:https://openapi.naver.com/v1/search/news.json}
    clientId: ${NAVER_CLIENT_ID:dummy}
    clientSecret: ${NAVER_CLIENT_SECRET:dummy}

slack:
  webhook:
    breaking: ${BREAKING_NEWS_WEBHOOK_URL}
    exclusive: ${EXCLUSIVE_NEWS_WEBHOOK_URL}
    develop: ${DEVELOP_WEBHOOK_URL}
```

---

## 10. 관찰성(Observability)
- **Actuator**: `/actuator/health`, `/actuator/metrics`
- **로그**: 단계별 태그(FETCH/FILTER/DELIVER/RETRY)로 구조화
- **메트릭**(예시):
  - 수집 기사 수, 필터링 드랍 수, 전송 시도/성공/실패 수
  - 재시도 횟수, 레이트리밋 발생 수, API 응답 시간

---

## 11. 오류 처리 & 재시도
- **HTTP 5xx/네트워크 오류**: 지수 백오프 + 지터로 N회 재시도 후 실패 기록
- **Slack 429(레이트리밋)**: 동일 전략 + 쿨다운 로그
- **DB 예외**: 고유키 충돌 시 중복으로 간주, 스킵 처리

---

## 12. 보안
- API 키/웹훅 URL: 환경변수로 주입, 로그 마스킹
- 네트워크 경계: 아웃바운드 전용, 필요 시 프록시/게이트웨이 적용

---

## 13. 배포/실행
- **로컬**: `.env` 준비 → IntelliJ에서 `NewsApplication` 실행 또는 `./gradlew bootRun`
- **컨테이너**(옵션): Dockerfile/K8s 매니페스트 추가 예정. `SERVER_PORT` 포함 환경변수 주입

---

## 14. 테스트 전략
- **외부 API 목킹**: `MockWebServer`로 네이버/슬랙 응답 시뮬레이션
- **R2DBC 통합 테스트**: Testcontainers(MySQL)로 스키마/쿼리 검증
- **시나리오 테스트**: 중복/제외/재시도/레이트리밋/장애복구 케이스

---

## 15. 마이그레이션 플랜(파일 → DB)
1) 기존 텍스트 파일(중복 키워드/제외 룰/마지막 전송 시각) 내용을 각 테이블로 Import
2) `.env` 유지하되 `APP_PORT`→`SERVER_PORT` rename
3) 파일 의존 로직 제거, DB 기반으로 동작 확인
4) 운영 전환 후 파일 키는 Deprecated → 제거

---

## 16. 성공 기준(수용 기준 & KPI)
- 중복 기사 **0건** 전송(동일 링크 기준)
- 제외 룰 적용 정확도 **100%**(등록 즉시 반영)
- 전송 성공률 **> 99%** (네이버/슬랙 정상 시)
- 장애 발생 시 자동 재시도 및 누락률 **< 1%**
- 기동/헬스/메트릭을 통해 상태 파악이 즉시 가능할 것

---

## 17. 향후 고도화 로드맵
- 유사도 판단(제목 토큰/벡터 검색)으로 정교한 중복/스팸 차단
- Slack 스레딩/리치 포맷(블록 키트) 지원
- 관리자 API/대시보드(제외 룰 CRUD, 전송 이력 조회)

---

## 18. 용어
- **채널(Channel)**: BREAKING(속보), EXCLUSIVE(단독), DEV(개발 테스트)
- **정규화 URL**: 파라미터/프래그먼트 제거하고 소문자화한 URL
