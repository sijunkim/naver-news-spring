package com.news.naver.data.enum

/**
 * 뉴스 채널의 종류를 정의하는 Enum 클래스입니다.
 * 각 채널은 네이버 뉴스 API 호출 시 사용될 쿼리 문자열을 가집니다.
 *
 * @property query 네이버 뉴스 API 호출 시 사용할 쿼리 문자열
 */
enum class NewsChannel(val query: String) {
    /** 속보 채널 */
    BREAKING("속보"),
    /** 단독 채널 */
    EXCLUSIVE("단독"),
    /** 개발/테스트 채널 */
    DEV("테스트");

    fun toExclusionScope(): ExclusionScope = when (this) {
        BREAKING -> ExclusionScope.BREAKING
        EXCLUSIVE -> ExclusionScope.EXCLUSIVE
        DEV -> ExclusionScope.ALL
    }
}
