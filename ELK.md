ELK 파이프라인 확인 절차

> 애플리케이션 로깅에서 Logstash 전송이 필요한 경우 `SPRING_PROFILES_ACTIVE`에 `elk`를 포함시켜야 합니다. (예: `SPRING_PROFILES_ACTIVE=local,elk`)
> 해당 프로필이 꺼져 있으면 콘솔 로그만 남기고 ELK 컨테이너가 없어도 애플리케이션이 정상 동작합니다.

1. 애플리케이션 실행 후 Logstash 연결 확인
    - docker-compose logs logstash로 Logstash가 TCP 포트를 열고 있는지, 애플리케이션에서 접속 성공 로그가 찍히는지 확인합니다.
    - 에러가 있다면 Logstash 파이프라인(app-logs.conf)에서 codec 설정, 포트, 인증 정보를 다시 점검합니다.
2. Logstash 파이프라인 상태 점검
    - curl http://localhost:9600/_node/pipelines?pretty (필요 시 인증 파라미터 추가)로 현재 동작 중인 파이프라인 목록과 처리량을 확인합
      니다.
    - 필터 단계에서 필드가 제대로 붙는지 확인하려면 curl http://localhost:9600/_node/stats/pipelines/app_logs?pretty처럼 파이프라인 ID를 지
      정해 처리 건수를 확인할 수 있습니다.
3. Elasticsearch 인덱스 생성 여부 확인
    - curl -u elastic:changeme http://localhost:9200/_cat/indices/naver-news-app-logs*?v로 로그 인덱스가 생성됐는지 확인합니다.
    - 건수를 확인하려면 curl -u elastic:changeme http://localhost:9200/naver-news-app-logs-*/_count를 사용합니다.
    - 필드 매핑이 기대와 일치하는지 확인하려면 curl -u elastic:changeme http://localhost:9200/naver-news-app-logs-*/_mapping?pretty.
4. JDBC 파이프라인(기사/전송/스팸 데이터) 검증
    - 해당 파이프라인에 증분 쿼리가 잘 적용되었는지 /usr/share/logstash/bin/logstash --config.test_and_exit로 문법 검사를 하고,
    - 실행 중에는 Logstash 로그에 sql_last_value 갱신 메시지가 나오는지, docker-compose logs logstash에서 주기적으로 쿼리가 돌고 있는지 확
      인합니다.
    - Elasticsearch에서 각 인덱스(naver-news-articles, naver-news-delivery, naver-news-spam)를 _cat/indices, _count로 조회해 데이터가 쌓이
      는지 체크합니다.
5. Kibana에서 확인
    - Kibana Dev Tools에서 위와 같은 _cat/_search를 직접 실행해도 되고,
    - 인덱스 패턴(naver-news-*)을 생성한 뒤 Discover 화면에서 신규 로그/데이터가 표시되는지 확인합니다.
    - 대시보드나 시각화가 있다면 시간 필터를 맞춰 최근 데이터가 들어오는지 검증합니다.
6. 문제 발생 시
    - 애플리케이션 로그에서 Logstash 연결 오류가 있는지 (connect_exception, SSLHandshakeException 등) 확인하고,
    - Logstash 로그에 “_jsonparsefailure”, “_grokparsefailure” 태그가 붙으면 필터 패턴을 수정해야 합니다.
    - JDBC 파이프라인이라면 DB 계정 권한, 시간대 변환(UTC/KST) 여부, sql_last_value 저장 파일 경로를 살펴봅니다.
