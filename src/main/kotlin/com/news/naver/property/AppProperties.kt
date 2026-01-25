package com.news.naver.property

import com.news.naver.data.dto.Duplicate
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 애플리케이션 전반에 걸친 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `app` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property duplicate 중복 처리 관련 설정
 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    @NestedConfigurationProperty
    val duplicate: Duplicate
)