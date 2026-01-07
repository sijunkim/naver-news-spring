package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * ELK(Elasticsearch, Logstash, Kibana) 활성화 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `elk` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property active ELK 스택 로깅 활성화 여부
 */
@ConfigurationProperties(prefix = "elk")
data class ElkProperties(
    val active: Boolean
)
