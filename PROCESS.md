# 뉴스 처리 프로세스 흐름도

이 문서는 local 프로필로 프로젝트를 시작했을 때 스케줄러가 실행되면서 거치는 로직들을 설명합니다.

## 목차
- [1. 뉴스 폴링 스케줄러 (NewsPollingScheduler)](#1-뉴스-폴링-스케줄러)
- [2. 일일 요약 스케줄러 (DailySummaryScheduler)](#2-일일-요약-스케줄러)

---

## 1. 뉴스 폴링 스케줄러

### 실행 주기
- **주기**: 60초마다 (설정: `app.poll.intervalSeconds`)
- **실행 방식**: `@Scheduled(fixedDelayString = "${app.poll.intervalSeconds:60}000")`

### 전체 흐름도

```
NewsPollingScheduler.poll()
    │
    ├─ [Profile 확인]
    │   ├─ local 프로필: NewsChannel.DEV만 처리
    │   └─ 기타 프로필: NewsChannel.BREAKING, EXCLUSIVE 처리
    │
    └─ NewsProcessingService.runOnce(channel)
        │
        ├─ 1️⃣ 마지막 폴링 시간 로드
        │   └─ RuntimeStateRepository.selectState("last_poll_time_{channel}")
        │
        ├─ 2️⃣ 뉴스 아이템 수집 (NewsItemProcessor)
        │   │
        │   ├─ NewsItemProcessor.fetchItems(query)
        │   │   └─ NaverNewsClient.search()
        │   │       └─ Naver Open API 호출
        │   │           └─ display=30, start=1, sort=date
        │   │
        │   ├─ NewsItemProcessor.filterByTime(items, lastPollTime)
        │   │   └─ pubDate가 lastPollTime 이후인 것만 필터링
        │   │
        │   └─ NewsItemProcessor.filterByChatGPT(items)
        │       │
        │       ├─ [ChatGPT API Key 확인]
        │       │   ├─ 설정됨: ChatGPT 필터링 수행
        │       │   └─ 미설정: 모든 아이템 그대로 반환
        │       │
        │       └─ ChatGPTClient.filterNewsTitlesAndReturnIndices(titles)
        │           ├─ 제목 리스트를 1,2,3... 형식으로 번호 매김
        │           ├─ ChatGPT에게 광고/연예/스포츠 뉴스 필터링 요청
        │           └─ 유효한 뉴스의 인덱스만 반환
        │
        ├─ 3️⃣ 뉴스 아이템 처리 (병렬)
        │   │
        │   └─ [각 아이템마다 processItem() 실행]
        │       │
        │       ├─ 제목/설명 정제 (NewsRefinerService)
        │       │   ├─ refineTitle(): HTML 태그 제거, 특수문자 정리
        │       │   └─ refineDescription(): HTML 태그 제거, 길이 제한
        │       │
        │       ├─ Step 1: 회사 정보 추출
        │       │   ├─ NewsRefinerService.extractCompany(link)
        │       │   │   └─ URL에서 언론사 도메인 추출 (예: joins, chosun)
        │       │   └─ NewsCompanyService.findOrCreateCompany(domain)
        │       │       ├─ DB에서 조회
        │       │       └─ 없으면 생성
        │       │
        │       ├─ Step 2: 기사 유효성 검증
        │       │   │
        │       │   ├─ 해시 생성
        │       │   │   └─ SHA256(normalizedUrl + companyId)
        │       │   │
        │       │   ├─ 중복 체크
        │       │   │   └─ NewsArticleRepository.countNewsArticleByHash(hash)
        │       │   │       └─ > 0이면 SKIPPED_DUPLICATE
        │       │   │
        │       │   ├─ 제외 룰 검사 (NewsFilterService)
        │       │   │   ├─ KeywordExclusionRepository에서 제외 키워드 조회
        │       │   │   ├─ PressExclusionRepository에서 제외 언론사 조회
        │       │   │   └─ 매칭되면 SKIPPED_RULE
        │       │   │
        │       │   └─ 스팸 검사 (NewsSpamFilterService)
        │       │       ├─ Redis에 키워드별 카운트 저장
        │       │       ├─ 3시간 윈도우 내 중복 체크
        │       │       └─ threshold(5회) 초과 시 SKIPPED_SPAM
        │       │
        │       ├─ Step 3: 기사 저장
        │       │   ├─ NewsArticleRepository.insertNewsArticle()
        │       │   │   └─ 저장 실패 시 FAILED_PERSIST
        │       │   └─ NewsArticleRepository.selectNewsArticleByHash()
        │       │       └─ 재조회 실패 시 FAILED_LOOKUP
        │       │
        │       └─ Step 4: 슬랙 전송 및 로깅
        │           ├─ SlackMessageFormatter.createPayload(news)
        │           │   └─ 슬랙 메시지 포맷 생성
        │           ├─ SlackClient.send(channel, payload)
        │           │   └─ Webhook URL로 POST 요청
        │           └─ DeliveryLogRepository.insertDeliveryLog()
        │               └─ 전송 결과 저장 (SUCCESS/FAILED)
        │
        ├─ 4️⃣ 마지막 폴링 시간 업데이트
        │   └─ RuntimeStateRepository.updateState("last_poll_time_{channel}", lastItemTime)
        │
        └─ 5️⃣ 처리 결과 로깅
            └─ eligible, sent, duplicateSkips, spamSkips, chatgptSkips 통계
```

### 상세 단계별 설명

#### Phase 1: 프로필 기반 채널 선택
```kotlin
// local 프로필
channelsToPoll = [NewsChannel.DEV]

// 기타 프로필 (prod 등)
channelsToPoll = [NewsChannel.BREAKING, NewsChannel.EXCLUSIVE]
```

#### Phase 2: 뉴스 수집 (Naver API)
```kotlin
NaverNewsClient.search(
    query = channel.query,  // "속보" or "단독"
    display = 30,
    start = 1,
    sort = "date"
)
```

**응답 예시:**
```json
{
  "items": [
    {
      "title": "<b>속보</b> 제목...",
      "link": "https://n.news.naver.com/...",
      "description": "뉴스 내용...",
      "pubDate": "Mon, 28 Dec 2025 10:30:00 +0900"
    }
  ]
}
```

#### Phase 3: ChatGPT 필터링 (선택적)

**ChatGPT API 키가 설정된 경우:**

```
입력: ["1. 속보 제목1", "2. 광고성 제목", "3. 속보 제목2"]
    ↓
ChatGPT 프롬프트:
"다음 뉴스 제목 중 광고성/연예/스포츠 뉴스를 제외하고 유효한 뉴스만 선택하세요.
응답 형식: 1,3,5,7 (번호만)"
    ↓
ChatGPT 응답: "1,3"
    ↓
결과: [Item0, Item2] 만 처리 대상
```

**ChatGPT API 키가 없는 경우:**
- 모든 아이템 그대로 처리
- 로그: "ChatGPT filtering disabled - returning all indices"

#### Phase 4: 스팸 검사 (Redis 기반)

```
제목: "삼성전자 주가 급등"
    ↓
토큰화: ["삼성전자", "주가", "급등"]
    ↓
Redis 키: news:spam:title:삼성전자, news:spam:title:주가, ...
    ↓
각 키워드 카운트 증가 (TTL: 3시간)
    ↓
카운트 >= 5 ? SKIPPED_SPAM : 계속 진행
```

#### Phase 5: 슬랙 메시지 포맷

```kotlin
{
  "text": "📰 [언론사명] 뉴스 제목\n\n내용 요약...\n\n🔗 원문: URL"
}
```

#### Phase 6: 처리 결과

```
DispatchStatus:
- SENT: 성공적으로 전송
- SKIPPED_DUPLICATE: 중복 기사
- SKIPPED_RULE: 제외 룰에 의해 스킵
- SKIPPED_SPAM: 스팸으로 판단
- SKIPPED_CHATGPT: ChatGPT가 필터링
- FAILED_PERSIST: DB 저장 실패
- FAILED_SLACK: 슬랙 전송 실패
```

---

## 2. 일일 요약 스케줄러

### 실행 주기
- **주기**: 매일 자정 00:00
- **실행 방식**: `@Scheduled(cron = "0 0 0 * * *")`

### 전체 흐름도

```
DailySummaryScheduler.generateAndSendDailySummary()
    │
    ├─ 대상 날짜 계산
    │   └─ yesterday = LocalDate.now().minusDays(1)
    │
    └─ DailySummaryService.generateAndSendDailySummary(yesterday)
        │
        ├─ 1️⃣ 발송 뉴스 조회
        │   │
        │   └─ NewsArticleRepository.selectDeliveredNewsInDateRange(startDateTime, endDateTime)
        │       │
        │       └─ SQL Query:
        │           SELECT
        │               na.id AS article_id,
        │               na.title,
        │               na.summary,
        │               MIN(dl.sent_at) AS first_sent_at,
        │               GROUP_CONCAT(DISTINCT dl.channel) AS channels
        │           FROM delivery_log dl
        │           INNER JOIN news_article na ON dl.article_id = na.id
        │           WHERE dl.status = 'SUCCESS'
        │             AND dl.sent_at >= :startDateTime
        │             AND dl.sent_at < :endDateTime
        │           GROUP BY na.id
        │           ORDER BY first_sent_at DESC
        │
        ├─ 2️⃣ ChatGPT 요약 생성 (선택적)
        │   │
        │   └─ ChatGPTClient.generateDailySummary(newsItems)
        │       │
        │       ├─ [ChatGPT API 키 확인]
        │       │   ├─ 설정됨: 요약 생성
        │       │   └─ 미설정: null 반환
        │       │
        │       └─ 프롬프트 생성 (최대 20개 뉴스)
        │           ├─ "오늘 발송된 뉴스 목록을 3-5문장으로 요약"
        │           ├─ "주요 키워드와 핵심 내용 위주"
        │           └─ "200자 이내로 작성"
        │
        ├─ 3️⃣ TOP 10 키워드 추출
        │   │
        │   └─ DailySummaryService.extractTopKeywords(newsItems, 10)
        │       │
        │       ├─ 한글 정규식으로 2자 이상 단어 추출
        │       │   └─ Regex("[가-힣]{2,}")
        │       │
        │       ├─ 불용어 필터링
        │       │   └─ STOP_WORDS = ["은", "는", "이", "가", ..., "속보", "단독"]
        │       │
        │       ├─ 빈도수 계산
        │       │   └─ groupingBy { it }.eachCount()
        │       │
        │       └─ 내림차순 정렬 후 TOP 10 반환
        │           └─ [(삼성전자, 15), (주가, 12), ...]
        │
        └─ 4️⃣ Slack 전송
            │
            └─ 메시지 포맷:
                📊 *일일 뉴스 발송 리포트 (2025-12-27)*

                ✅ *발송 건수:* 45건

                📝 *요약:*
                오늘은 반도체 업계 동향과 경제 정책 관련 뉴스가 주를 이뤘습니다.
                삼성전자의 신제품 출시와 정부의 세제 개편안이 주목받았습니다.

                🔑 *TOP 10 키워드:*
                1. 삼성전자 (15회)
                2. 주가 (12회)
                3. 정부 (10회)
                ...
```

### 상세 단계별 설명

#### Phase 1: 날짜 범위 계산

```kotlin
// 오늘이 2025-12-28 00:00:00 이라면
yesterday = 2025-12-27
startDateTime = 2025-12-27 00:00:00
endDateTime = 2025-12-28 00:00:00
```

#### Phase 2: 발송 뉴스 조회 결과 예시

```kotlin
[
  DailyNewsItem(
    articleId = 1234,
    title = "삼성전자, 신제품 출시",
    summary = "삼성전자가...",
    firstSentAt = 2025-12-27 09:30:00,
    channels = "BREAKING,EXCLUSIVE"
  ),
  ...
]
```

#### Phase 3: ChatGPT 요약

**API 키가 설정된 경우:**
```
프롬프트:
"다음은 오늘 발송된 뉴스 목록입니다.
1. 삼성전자, 신제품 출시 - 삼성전자가...
2. 정부, 세제 개편안 발표 - 정부가...
...

이 뉴스들의 공통 주제와 핵심 내용을 3-5문장으로 간결하게 요약해주세요."

ChatGPT 응답:
"오늘은 반도체 업계 동향과 경제 정책 관련 뉴스가 주를 이뤘습니다..."
```

**API 키가 없는 경우:**
```
요약: "요약 생성 실패 (ChatGPT API 미설정 또는 요청 실패)"
```

#### Phase 4: 키워드 추출 과정

```
입력 제목들:
["삼성전자 신제품 출시", "삼성전자 주가 상승", "정부 세제 개편"]
    ↓
정규식 추출:
["삼성전자", "신제품", "출시", "삼성전자", "주가", "상승", "정부", "세제", "개편"]
    ↓
불용어 제거:
["삼성전자", "신제품", "출시", "삼성전자", "주가", "상승", "정부", "세제", "개편"]
(은/는/이/가 등은 없음)
    ↓
빈도수 계산:
{삼성전자: 2, 주가: 1, 신제품: 1, ...}
    ↓
정렬 및 TOP 10:
[(삼성전자, 2), (주가, 1), (신제품, 1), ...]
```

---

## 데이터 흐름 다이어그램

### 뉴스 폴링 데이터 흐름

```
Naver API
    ↓ (JSON)
NaverNewsClient
    ↓ (List<Item>)
NewsItemProcessor
    ↓ (Filtered List<Item>)
NewsProcessingService
    ↓ (각 Item 처리)
    ├→ NewsCompanyService → news_company 테이블
    ├→ NewsArticleRepository → news_article 테이블
    ├→ SlackClient → Slack Webhook
    └→ DeliveryLogRepository → delivery_log 테이블
```

### 일일 요약 데이터 흐름

```
delivery_log + news_article 테이블
    ↓ (JOIN)
NewsArticleRepository
    ↓ (List<DailyNewsItem>)
DailySummaryService
    ├→ ChatGPTClient → ChatGPT API → 요약 텍스트
    └→ extractTopKeywords() → TOP 10 키워드
         ↓
    SlackClient → Slack Webhook (DEV 채널)
```

---

## 주요 설정값

### application.yml

```yaml
app:
  poll:
    interval-seconds: 60  # 폴링 주기 (초)
  duplicate:
    threshold: 5  # 스팸 판단 임계값

naver:
  openapi:
    url: https://openapi.naver.com/v1/search/news.json
    client-id: ${NAVER_CLIENT_ID}
    client-secret: ${NAVER_CLIENT_SECRET}

slack:
  webhook:
    breaking: ${BREAKING_NEWS_WEBHOOK_URL}
    exclusive: ${EXCLUSIVE_NEWS_WEBHOOK_URL}
    develop: ${DEVELOP_WEBHOOK_URL}

chatgpt:
  api-key: ${CHATGPT_API_KEY:}  # 선택적 설정
```

### 환경변수

| 변수명 | 필수 | 설명 |
|--------|------|------|
| `NAVER_CLIENT_ID` | ✅ | Naver Open API Client ID |
| `NAVER_CLIENT_SECRET` | ✅ | Naver Open API Client Secret |
| `BREAKING_NEWS_WEBHOOK_URL` | ✅ | 속보 채널 Slack Webhook URL |
| `EXCLUSIVE_NEWS_WEBHOOK_URL` | ✅ | 단독 채널 Slack Webhook URL |
| `DEVELOP_WEBHOOK_URL` | ✅ | 개발 채널 Slack Webhook URL |
| `CHATGPT_API_KEY` | ❌ | ChatGPT API 키 (선택) |
| `REDIS_HOST` | ✅ | Redis 호스트 (스팸 필터링) |
| `DB_HOST` | ✅ | MySQL 호스트 |

---

## 로컬 프로필 실행 시 특징

### 1. 채널 제한
- **DEV 채널만 처리**
- BREAKING, EXCLUSIVE 채널은 처리하지 않음

### 2. 로그 레벨
- 개발 환경에 맞는 상세한 로깅
- DEBUG 레벨 활성화 가능

### 3. 테스트 API 사용
- `POST /test/message`: 메시지 전송 테스트
- `POST /test/news`: 키워드 뉴스 전송 테스트
- `POST /test/news/channel`: 채널별 뉴스 전송 테스트

### 4. ChatGPT 선택적 사용
- API 키 미설정 시: 모든 뉴스 전송
- API 키 설정 시: 필터링 활성화

---

## 에러 처리

### 1. Naver API 호출 실패
- 로그 출력 후 빈 리스트 반환
- 다음 폴링 주기에 재시도

### 2. ChatGPT API 호출 실패
- 로그 출력 후 모든 뉴스 전송 (fallback)
- 필터링 없이 진행

### 3. Redis 연결 실패
- Health check 실패 시 스팸 필터 비활성화
- 3초마다 재연결 시도

### 4. Slack 전송 실패
- 실패 상태로 delivery_log에 기록
- HTTP 상태 코드와 응답 본문 저장

### 5. Database 저장 실패
- 로그 출력 및 DispatchStatus.FAILED_PERSIST
- 다음 폴링에서 재시도 가능 (중복 체크)

---

## 모니터링 포인트

### 1. 로그 확인
```
# Kibana에서 확인 가능한 로그 필드
- channel: 채널명 (DEV, BREAKING, EXCLUSIVE)
- eligible: 처리 대상 뉴스 수
- sent: 전송 성공 수
- duplicateSkips: 중복 스킵 수
- spamSkips: 스팸 스킵 수
- chatgptSkips: ChatGPT 필터링 수
```

### 2. 데이터베이스 테이블
```sql
-- 처리된 뉴스
SELECT * FROM news_article ORDER BY fetched_at DESC LIMIT 10;

-- 전송 로그
SELECT * FROM delivery_log ORDER BY sent_at DESC LIMIT 10;

-- 런타임 상태 (마지막 폴링 시간)
SELECT * FROM runtime_state WHERE `key` LIKE 'last_poll_time%';
```

### 3. Redis 키
```bash
# 스팸 키워드 카운트
KEYS news:spam:title:*

# 특정 키워드 카운트 확인
GET news:spam:title:삼성전자

# TTL 확인
TTL news:spam:title:삼성전자  # 남은 시간(초)
```

---

## 성능 최적화

### 1. 병렬 처리
- 채널별 병렬 처리 (async/await)
- 아이템별 병렬 처리 (coroutineScope)

### 2. 데이터베이스 인덱싱
```sql
-- 중복 체크 최적화
CREATE UNIQUE INDEX idx_hash ON news_article(naver_link_hash);

-- 시간 범위 조회 최적화
CREATE INDEX idx_published_at ON news_article(published_at DESC);

-- 회사별 조회 최적화
CREATE INDEX idx_company_id ON news_article(company_id);

-- 전송 로그 조회 최적화
CREATE INDEX idx_sent_at ON delivery_log(sent_at DESC);
```

### 3. Redis 캐싱
- 스팸 키워드: TTL 3시간
- 연결 상태: 3초마다 health check

---

## 트러블슈팅

### Q1: 뉴스가 전송되지 않음
**확인 사항:**
1. Naver API 호출 성공 여부
2. ChatGPT 필터링으로 모두 제외되었는지
3. 스팸 필터로 모두 제외되었는지
4. 제외 룰(keyword_exclusion, press_exclusion)에 걸렸는지

### Q2: 중복 뉴스가 계속 전송됨
**확인 사항:**
1. `last_poll_time` 업데이트 여부
2. URL 정규화 로직 확인
3. 해시 생성 로직 확인

### Q3: 일일 요약이 발송되지 않음
**확인 사항:**
1. 스케줄러 활성화 여부 (`@EnableScheduling`)
2. 어제 날짜에 발송된 뉴스가 있는지
3. ChatGPT API 호출 실패 여부 (선택적이므로 키워드는 전송됨)

### Q4: Redis 연결 실패
**확인 사항:**
1. Redis 서버 실행 여부
2. 연결 정보 (host, port, password)
3. 네트워크 방화벽 설정

---

## 참고 문서
- [TEST_API_GUIDE.md](./TEST_API_GUIDE.md): 테스트 API 사용 가이드
- [README.md](./README.md): 프로젝트 전체 설정 및 실행 가이드
