package com.news.naver.property

import com.news.naver.data.dto.Webhook
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Slack 관련 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `slack` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property webhook 웹훅 관련 설정
 */
@ConfigurationProperties(prefix = "slack")
data class SlackProperties(
    val webhook: Webhook
) {
    fun urlFor(channel: com.news.naver.data.enum.NewsChannel): String = when (channel) {
        com.news.naver.data.enum.NewsChannel.BREAKING -> webhook.breaking
        com.news.naver.data.enum.NewsChannel.EXCLUSIVE -> webhook.exclusive
        com.news.naver.data.enum.NewsChannel.DEV -> webhook.develop
    }
}