package com.news.naver.data.dto

/**
 * 네이버 Open API의 상세 설정을 담는 데이터 클래스입니다.
 *
 * @property url API 엔드포인트 URL
 * @property clientId 클라이언트 ID
 * @property clientSecret 클라이언트 Secret
 */
data class OpenApi(
    val url: String,
    val clientId: String,
    val clientSecret: String
)
