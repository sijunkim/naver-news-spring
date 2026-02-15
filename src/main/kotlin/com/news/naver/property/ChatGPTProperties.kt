package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * ChatGPT API 관련 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `chatgpt` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property enabled ChatGPT 기능 활성화 여부 (true/false)
 * @property apiKey ChatGPT API Key
 * @property apiUrl ChatGPT API URL
 * @property model ChatGPT 모델명 (예: gpt-4o-mini, gpt-4, gpt-3.5-turbo)
 * @property temperature 응답 창의성 제어 (0.0~2.0, 낮을수록 일관성↑)
 * @property maxTokens 기본 최대 토큰 수
 * @property summaryMaxTokens 요약 생성 시 최대 토큰 수
 */
@ConfigurationProperties(prefix = "chatgpt")
data class ChatGPTProperties(
    val enabled: Boolean = true,
    val apiKey: String?,
    val apiUrl: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val summaryMaxTokens: Int
) {
    /**
     * ChatGPT 기능이 활성화되어 있는지 확인합니다.
     * enabled 플래그가 true이고, API Key가 설정되어 있어야 활성화됩니다.
     */
    fun isEnabled(): Boolean = enabled && !apiKey.isNullOrBlank()
}
