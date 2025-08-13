package com.news.naver.data.dto

/**
 * 뉴스 중복 처리 관련 설정을 담는 데이터 클래스입니다.
 *
 * @property threshold 중복으로 간주할 키워드 임계치
 */
data class Duplicate(
    val threshold: Int
)
