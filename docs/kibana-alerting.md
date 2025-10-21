# Kibana에서 Redis 장애 알림 설정하기

## 사전 준비
- `.env` 파일의 `BREAKING_NEWS_WEBHOOK_URL`(또는 필요한 채널용 웹훅)을 확인합니다. Kibana에서 Slack 커넥터를 만들 때 해당 URL을 그대로 사용합니다.
- ELK 스택이 정상 동작하고 있으며, 애플리케이션 로그가 Elasticsearch에 적재되고 있어야 합니다.

## 1. Slack 커넥터 생성
1. Kibana에 접속한 뒤 **Stack Management → Rules and Connectors → Connectors**로 이동합니다.
2. **Create connector**를 클릭하고 **Slack** 유형을 선택합니다.
3. `Name`에 식별하기 쉬운 이름(예: `Slack - Breaking News`)을 입력합니다.
4. `.env`의 `BREAKING_NEWS_WEBHOOK_URL` 값을 **Webhook URL**에 입력합니다.
5. 저장 후 **Run connector** 버튼으로 테스트 메시지가 정상 전송되는지 확인합니다.

## 2. Redis 장애 감지 규칙 만들기
1. 동일한 화면에서 **Rules** 탭으로 이동한 뒤 **Create rule**을 선택합니다.
2. Rule type으로 **Logs threshold** 또는 **Elasticsearch query**(KQL 기반)를 선택합니다.
   - 예시 쿼리: `log.level: WARN and message: "status=redis_down"`
3. Time range는 1분 단위(필요 시 조정)로, `When` 조건은 `matches is above 0`과 같이 설정해 로그가 한 건이라도 발생하면 알림이 발송되도록 합니다.
4. `Actions` 섹션에서 앞서 만든 Slack 커넥터를 선택하고 메시지 템플릿을 작성합니다.
   - 예시:
     ```
     Redis 장애 감지됨 🔥
     최근 1분 내 {context.matching_documents} 건의 경고 로그가 수집되었습니다.
     확인이 필요합니다.
     ```
5. Rule 이름과 태그를 설정하고 저장합니다.

## 3. Redis 복구 알림 추가 (선택)
- 복구 로그(`status=redis_recovered`)에 대해서도 동일한 방식으로 별도 규칙을 만들면 Slack으로 장애/복구를 모두 통보할 수 있습니다.

## 4. 모니터링 및 유지보수
- Kibana 대시보드에서 해당 규칙의 실행 이력과 Slack 알림 성공 여부를 주기적으로 점검합니다.
- 웹훅 URL이 변경되거나 Slack 채널을 분리하고 싶을 때는 커넥터 설정만 업데이트하면 됩니다.
- 필요 시 Elastic Alerting의 스케줄, 임계값, 지연시간을 조정해 환경에 맞게 튜닝하세요.
