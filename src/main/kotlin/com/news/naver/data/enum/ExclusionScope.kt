package com.news.naver.data.enum

/**
 * 키워드 제외 규칙이 적용될 범위를 정의하는 Enum 클래스입니다.
 */
enum class ExclusionScope {
    /** 속보 채널에만 적용 */
    BREAKING,
    /** 단독 채널에만 적용 */
    EXCLUSIVE,
    /** 모든 채널에 적용 */
    ALL
}
