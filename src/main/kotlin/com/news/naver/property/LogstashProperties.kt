package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Logstash 연결 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `logstash` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property host Logstash 서버 호스트
 * @property tcpPort Logstash TCP 포트 (로그 전송용)
 */
@ConfigurationProperties(prefix = "logstash")
data class LogstashProperties(
    val host: String,
    val tcpPort: Int
)
