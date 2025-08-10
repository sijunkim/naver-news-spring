package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 애플리케이션 전반에 걸친 설정을 담는 ConfigurationProperties 클래스입니다。
 * `application.yml` 또는 `.env` 파일의 `app` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property poll 폴링 관련 설정
 * @property duplicate 중복 처리 관련 설정
 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val poll: Poll,
    val duplicate: Duplicate
) {
    /**
     * 뉴스 폴링 주기에 대한 설정을 담는 데이터 클래스입니다.
     *
     * @property intervalSeconds 뉴스 수집 주기 (초 단위)
     */
    data class Poll(
        val intervalSeconds: Long
    )

    /**
     * 뉴스 중복 처리 관련 설정을 담는 데이터 클래스입니다.
     *
     * @property threshold 중복으로 간주할 키워드 임계치
     */
    data class Duplicate(
        val threshold: Int
    )
}

