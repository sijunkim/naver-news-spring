package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * ChatGPT API 관련 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `chatgpt` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property apiKey ChatGPT API Key
 */
@ConfigurationProperties(prefix = "chatgpt")
data class ChatGPTProperties(
    val apiKey: String?
) {
    /**
     * API Key가 설정되어 있는지 확인합니다.
     */
    fun isEnabled(): Boolean = !apiKey.isNullOrBlank()
}
