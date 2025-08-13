package com.news.naver.data.dto

/**
 * Slack 웹훅 URL 설정을 담는 데이터 클래스입니다.
 *
 * @property breaking 속보 채널 웹훅 URL
 * @property exclusive 단독 채널 웹훅 URL
 * @property develop 개발/테스트 채널 웹훅 URL
 */
data class Webhook(
    val breaking: String,
    val exclusive: String,
    val develop: String
)
