package com.news.naver.data.dto

/**
 * 일일 요약 스케줄링 설정을 담는 데이터 클래스입니다.
 *
 * @property cron Cron 표현식 (예: "0 0 0 * * *" = 매일 자정)
 */
data class DailySummary(
    val cron: String
)
