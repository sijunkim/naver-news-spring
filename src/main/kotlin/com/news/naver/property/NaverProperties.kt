package com.news.naver.property

import com.news.naver.data.dto.OpenApi
import com.news.naver.data.dto.Search
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 네이버 API 관련 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `naver` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property openapi Open API 관련 설정
 * @property search 검색 관련 설정
 */
@ConfigurationProperties(prefix = "naver")
data class NaverProperties(
    @NestedConfigurationProperty
    val openapi: OpenApi,
    @NestedConfigurationProperty
    val search: Search
)