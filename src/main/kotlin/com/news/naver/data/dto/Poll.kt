package com.news.naver.data.dto

/**
 * 뉴스 폴링 주기에 대한 설정을 담는 데이터 클래스입니다.
 *
 * @property intervalSeconds 뉴스 수집 주기 (초 단위)
 */
data class Poll(
    val intervalSeconds: Long
)
