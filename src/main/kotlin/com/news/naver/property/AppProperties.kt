package com.news.naver.property

import com.news.naver.data.dto.DailySummary
import com.news.naver.data.dto.Duplicate
import com.news.naver.data.dto.Poll
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 애플리케이션 전반에 걸친 설정을 담는 ConfigurationProperties 클래스입니다。
 * `application.yml` 또는 `.env` 파일의 `app` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property poll 폴링 관련 설정
 * @property duplicate 중복 처리 관련 설정
 * @property dailySummary 일일 요약 스케줄 관련 설정
 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    @NestedConfigurationProperty
    val poll: Poll,
    @NestedConfigurationProperty
    val duplicate: Duplicate,
    @NestedConfigurationProperty
    val dailySummary: DailySummary
)