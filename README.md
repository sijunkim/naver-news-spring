# PRD: 네이버 뉴스(속보/단독) 수집 → Slack 전송 (Spring WebFlux + Kotlin Coroutines + R2DBC)

## 0. TL;DR
네이버 뉴스에서 **속보/단독**을 주기적으로 수집하고(기본 60초), 필터링/중복 방지 후 **Slack Webhook**으로 전송하는 비동기 파이프라인입니다.  
이전 NestJS 프로젝트의 파일 기반 상태 관리(텍스트 파일)를 Spring WebFlux 프로젝트로 포팅합니다.

NestJS GitHub: https://github.com/sijunkim/naver-news  
Spring GitHub: https://github.com/sijunkim/naver-news-spring  

---

## 1. 배경 및 목표
- **배경:** 기존 NestJS 기반 구현은 텍스트 파일로 상태(중복 키워드, 제외 키워드/언론사, 마지막 전송 시각)를 관리했습니다. 스케일/복구성/운영가시성 측면에서 한계가 있어 DB 기반으로 고도화합니다.
- **목표:**
  1) 완전 비동기 코루틴(Spring WebFlux + Kotlin)로 수집/전송 파이프라인 구성
  2) **DB(R2DBC)** 로 상태/로그 영속화 (파일 의존성 제거)
  3) **중복 방지**(URL 정규화 + 해시), **제외 룰**(키워드/언론사) 적용
  4) **재시도/백오프/레이트리밋** 대응 및 **관찰성(로그/메트릭/헬스체크)** 강화
  5) 운영/개발 환경에서 `.env` 사용

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
- **Spring WebFlux + Kotlin Coroutines**: 논블로킹 비동기 처리
- **R2DBC(MySQL)**: 논블로킹 DB I/O
- **WebClient**: 네이버/Slack 외부 HTTP 호출
- **Scheduler(@Scheduled)**: 폴링 트리거 (각 채널 병렬 실행)
- **Config(@ConfigurationProperties)**: `.env` 값 바인딩 및 주입
- **Resilience(추가 예정)**: 필요 시 Resilience4j로 서킷브레이커/리트라이 적용
- **Actuator**: 헬스/메트릭 노출

---

## 5. 데이터 모델
```sql
CREATE TABLE `delivery_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `article_id` bigint NOT NULL,
  `channel` enum('BREAKING','EXCLUSIVE','DEV') NOT NULL,
  `status` enum('SUCCESS','RETRY','FAILED') NOT NULL,
  `http_status` int DEFAULT NULL,
  `sent_at` datetime NOT NULL,
  `response_body` text,
  PRIMARY KEY (`id`),
  KEY `idx_sent_at` (`sent_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `keyword_exclusion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scope` enum('BREAKING','EXCLUSIVE','ALL') NOT NULL,
  `keyword` varchar(200) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_scope_keyword` (`scope`,`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `news_article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(500) NOT NULL,
  `summary` text,
  `company_id` bigint DEFAULT NULL,
  `naver_link_hash` char(64) NOT NULL,
  `naver_link` varchar(399) DEFAULT NULL,
  `original_link` varchar(400) DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `fetched_at` datetime NOT NULL,
  `raw_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_published_at` (`published_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `news_company` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `domain_prefix` varchar(255) NOT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `domain_prefix` (`domain_prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `press_exclusion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `press_name` varchar(100) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_press` (`press_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `runtime_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `key` varchar(100) DEFAULT NULL,
  `value` text NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `key` (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `spam_keyword_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `keyword` varchar(200) NOT NULL,
  `count` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `keyword_UNIQUE` (`keyword`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
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

### 8.3 ChatGPT API (선택 사항)
- **목적**: 광고성 뉴스, 연예 뉴스, 스포츠 뉴스 자동 필터링
- **환경변수**: `CHATGPT_API_KEY` (OpenAI API Key)
- **동작 방식**:
  - API Key가 설정되지 않은 경우: 모든 뉴스가 정상 발송됩니다
  - API Key가 설정된 경우: ChatGPT가 뉴스 제목을 분석하여 필터링합니다
  - API 요청 실패 시: Fallback으로 모든 뉴스가 발송됩니다 (안전한 동작 보장)
- **로그**: API Key 미설정 시 INFO 레벨로 "ChatGPT filtering disabled - all news will be delivered" 로그 출력

### 8.4 수동 실행 API
- `POST /manual/news/dev`: 개발 채널용 뉴스 수집을 즉시 실행합니다.
- `POST /manual/news/breaking`: 속보 수집을 즉시 실행합니다.
- `POST /manual/news/exclusive`: 단독 수집을 즉시 실행합니다.
- `DELETE /manual/keywords/spam`: 스팸 키워드 로그를 모두 삭제합니다.
- `DELETE /manual/polls/timestamp`: 마지막 수집 시각을 삭제하여 다음 수집 시 전체 기사를 대상으로 하도록 합니다.
- `DELETE /manual/reset-all-data`: 모든 런타임 데이터를 삭제합니다.
- `GET /manual/domain-check?domain={domain}`: 도메인의 대표 타이틀을 확인합니다.

---

## 9. 환경변수
```
# .env

# 로컬 데이터베이스
DB_HOST=localhost
DB_PORT=
DB_DATABASE=naver_news
DB_USER=
DB_PASSWORD=

# 서버 포트
APP_PORT=3579

# 뉴스 배치 주기
POLL_INTERVAL_SECONDS=60

# 뉴스 중복 체크 카운트
DUPLICATED_COUNT=5

# 네이버 클라이언트 정보
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
NAVER_OPENAPI_URL=https://openapi.naver.com/v1/search/news.json

# 네이버 뉴스 검색 옵션
NAVER_SEARCH_DISPLAY=30
NAVER_SEARCH_START=1
NAVER_SEARCH_SORT=date

# 슬랙 웹훅 정보
# 속보 받을 웹훅 주소
BREAKING_NEWS_WEBHOOK_URL=
# 단독 받을 웹훅 주소
EXCLUSIVE_NEWS_WEBHOOK_URL=
# 테스트 웹훅 주소
DEVELOP_WEBHOOK_URL=

# ChatGPT API (선택 사항 - 광고성/연예/스포츠 뉴스 필터링)
CHATGPT_API_KEY=
CHATGPT_API_URL=https://api.openai.com/v1/chat/completions
```

- app.* → AppProperties.kt
- naver.openapi.* → NaverProperties.kt
- slack.webhook.* → SlackProperties.kt
- chatgpt.* → ChatGPTProperties.kt

---

## 10. 관찰성(Observability)
- **Actuator**: `/actuator/health`, `/actuator/metrics`
- **로그**: 단계별 태그(FETCH/FILTER/DELIVER/RETRY)로 구조화
- **메트릭**(예시):
  - 수집 기사 수, 필터링 드랍 수, 전송 시도/성공/실패 수
  - 재시도 횟수, 레이트리밋 발생 수, API 응답 시간

### 10.1 Logstash 연동(선택)
- 기본값은 콘솔 로그만 출력하고 `.env`의 `ELK_ACTIVE=false` 상태로 실행됩니다.
- Logstash/Elasticsearch에 로그를 전송하고 싶다면 `ELK_ACTIVE=true`를 환경 변수로 전달하면 됩니다. 예)
  - `.env` 수정: `ELK_ACTIVE=true`
  - 1회성 실행: `ELK_ACTIVE=true ./gradlew bootRun`
- `ELK_ACTIVE=true`일 때만 `LOGSTASH_HOST`, `LOGSTASH_TCP_PORT` 등의 환경 변수를 읽어 TCP 소켓으로 로그를 보냅니다.
- 운영 Compose(`docker compose up -d`)는 `.env` 값만 반영하므로, ELK를 붙이고 싶을 때는 `.env` 또는 `docker compose --env-file`에서 플래그를 켜 주세요.
- 로컬 개발 전용 동작은 `SPRING_PROFILES_ACTIVE=local`로 제어합니다(DEV 채널만 처리). 운영/테스트 Compose에서는 프로필을 비워둡니다.

---

## 11. 향후 고도화 로드맵
- Local LLM(gemma3:4b) 연동으로 기사 요약/분석/태깅
- 유사도 판단(제목 토큰/벡터 검색)으로 정교한 중복/스팸 차단
- Slack 스레딩/리치 포맷(블록 키트) 지원
- 관리자 API/대시보드(제외 룰 CRUD, 전송 이력 조회)

---

## 12. 용어
- **채널(Channel)**: BREAKING(속보), EXCLUSIVE(단독), DEV(개발 테스트)
- **정규화 URL**: 파라미터/프래그먼트 제거하고 소문자화한 URL

```
