# 테스트 API 가이드

이 문서는 DB에 영향을 주지 않고 뉴스 전송 기능을 테스트할 수 있는 API 사용법을 설명합니다.

## 사전 준비

1. 애플리케이션 실행
```bash
./gradlew bootRun
```

2. 기본 포트: `3579` (환경변수 `APP_PORT`로 변경 가능)

## API 엔드포인트

### 1. 임시 메시지 전송

**설명**: 임의의 메시지를 DEVELOP 웹훅으로 전송합니다.

**URL**: `POST /api/test/send-message`

**요청 예시**:
```bash
curl -X POST http://localhost:3579/api/test/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "message": "✅ 테스트 메시지입니다"
  }'
```

**응답 예시**:
```json
{
  "success": true,
  "message": "메시지가 성공적으로 전송되었습니다.",
  "channel": "DEV"
}
```

---

### 2. 뉴스 전송 (속보/단독)

**설명**: 네이버 뉴스 API에서 '속보' 또는 '단독' 키워드로 뉴스를 검색하여 DEVELOP 웹훅으로 전송합니다.
**중요**: DB에 저장하지 않으므로 인프라 영향이 없습니다.

**URL**: `POST /api/test/send-news`

**파라미터**:
- `keyword`: "속보" 또는 "단독" (필수)
- `maxItems`: 최대 전송 개수 (선택, 기본값: 10)

**요청 예시 1 - 속보 뉴스 10개 전송**:
```bash
curl -X POST http://localhost:3579/api/test/send-news \
  -H "Content-Type: application/json" \
  -d '{
    "keyword": "속보",
    "maxItems": 10
  }'
```

**요청 예시 2 - 단독 뉴스 5개 전송**:
```bash
curl -X POST http://localhost:3579/api/test/send-news \
  -H "Content-Type: application/json" \
  -d '{
    "keyword": "단독",
    "maxItems": 5
  }'
```

**응답 예시**:
```json
{
  "channel": "DEV",
  "totalFetched": 30,
  "filtered": {
    "timeFiltered": 0,
    "chatGptFiltered": 8,
    "ruleFiltered": 2,
    "spamFiltered": 1
  },
  "delivered": [
    {
      "title": "[속보] 주요 뉴스 제목",
      "link": "https://news.naver.com/...",
      "company": "뉴스사",
      "pubDate": "Mon, 21 Dec 2025 10:30:00 +0900"
    }
  ],
  "failed": [
    {
      "title": "필터링된 뉴스",
      "reason": "Filtered by exclusion rule"
    }
  ]
}
```

**응답 필드 설명**:
- `totalFetched`: 네이버 API에서 가져온 전체 뉴스 개수
- `filtered`: 각 필터링 단계에서 제외된 뉴스 개수
  - `timeFiltered`: 시간 필터링으로 제외된 개수
  - `chatGptFiltered`: ChatGPT 필터링으로 제외된 개수 (광고/연예)
  - `ruleFiltered`: 제외 룰로 제외된 개수
  - `spamFiltered`: 스팸 필터링으로 제외된 개수
- `delivered`: 성공적으로 전송된 뉴스 목록
- `failed`: 전송 실패한 뉴스 목록

---

### 3. 특정 채널로 뉴스 전송 (고급)

**설명**: 특정 채널을 지정하여 뉴스를 전송합니다.

**URL**: `POST /api/test/send-news-by-channel`

**파라미터**:
- `channel`: "BREAKING", "EXCLUSIVE", "DEV" 중 하나 (필수)
- `maxItems`: 최대 전송 개수 (선택)

**요청 예시**:
```bash
curl -X POST http://localhost:3579/api/test/send-news-by-channel \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "DEV",
    "maxItems": 5
  }'
```

---

## 테스트 시나리오

### 시나리오 1: 기본 메시지 전송 확인
```bash
# 1. 임시 메시지 전송
curl -X POST http://localhost:3579/api/test/send-message \
  -H "Content-Type: application/json" \
  -d '{"message": "🚀 Slack 연동 테스트"}'

# 2. Slack 웹훅 확인
# DEVELOP_WEBHOOK_URL로 설정된 슬랙 채널에서 메시지 확인
```

### 시나리오 2: 속보 뉴스 전송
```bash
# 1. 속보 뉴스 최대 10개 전송
curl -X POST http://localhost:3579/api/test/send-news \
  -H "Content-Type: application/json" \
  -d '{"keyword": "속보", "maxItems": 10}'

# 2. 응답에서 delivered 배열 확인
# 3. Slack에서 전송된 뉴스 확인
```

### 시나리오 3: 단독 뉴스 전송
```bash
# 1. 단독 뉴스 최대 10개 전송
curl -X POST http://localhost:3579/api/test/send-news \
  -H "Content-Type: application/json" \
  -d '{"keyword": "단독", "maxItems": 10}'

# 2. 응답에서 delivered 배열 확인
# 3. Slack에서 전송된 뉴스 확인
```

### 시나리오 4: ChatGPT 필터링 확인
```bash
# ChatGPT 필터링이 활성화된 경우 (CHATGPT_API_KEY 설정됨)
curl -X POST http://localhost:3579/api/test/send-news \
  -H "Content-Type: application/json" \
  -d '{"keyword": "속보", "maxItems": 10}'

# 응답의 filtered.chatGptFiltered 값을 확인하여
# 광고/연예 뉴스가 필터링되었는지 확인
```

---

## 주의사항

1. **DB 저장 없음**: 이 API들은 DB에 저장하지 않으므로 중복 체크가 동작하지 않습니다.
2. **웹훅 URL**: DEVELOP_WEBHOOK_URL로 설정된 슬랙 채널로만 전송됩니다.
3. **인프라 요구사항**:
   - MySQL: 불필요 (NewsCompanyService는 메모리 캐시 사용)
   - Redis: 불필요
   - 네이버 API: 필요 (NAVER_CLIENT_ID, NAVER_CLIENT_SECRET)
   - Slack 웹훅: 필요 (DEVELOP_WEBHOOK_URL)
   - ChatGPT API: 선택 (CHATGPT_API_KEY)

---

## 트러블슈팅

### 400 Bad Request
- 요청 본문의 JSON 형식을 확인하세요.
- `keyword`가 "속보" 또는 "단독"인지 확인하세요.

### 500 Internal Server Error
- 네이버 API 키가 올바른지 확인하세요 (.env 파일)
- Slack 웹훅 URL이 올바른지 확인하세요 (.env 파일)
- 애플리케이션 로그를 확인하세요

### 슬랙에 메시지가 안 옴
- DEVELOP_WEBHOOK_URL이 올바른지 확인하세요
- 응답의 `success` 필드를 확인하세요
- 웹훅 URL이 만료되지 않았는지 확인하세요

---

## 기존 프로덕션 로직과의 차이

| 기능 | 테스트 API | 프로덕션 (스케줄러) |
|------|-----------|-------------------|
| DB 저장 | ❌ 저장 안함 | ✅ 저장함 |
| 중복 체크 | ❌ 안함 | ✅ 함 |
| 시간 필터링 | ❌ 안함 (선택) | ✅ 함 |
| ChatGPT 필터링 | ✅ 함 | ✅ 함 |
| 룰/스팸 필터링 | ✅ 함 | ✅ 함 |
| 슬랙 전송 | ✅ 함 | ✅ 함 |
| 인프라 의존성 | 최소 | MySQL + Redis |

테스트 API는 빠른 테스트와 디버깅을 위해 설계되었으며, 프로덕션 로직과 핵심 비즈니스 로직을 공유합니다.
