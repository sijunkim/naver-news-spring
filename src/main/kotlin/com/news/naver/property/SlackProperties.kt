package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "slack")
data class SlackProperties(
    val webhook: Webhook
) {
    data class Webhook(
        val breaking: String,
        val exclusive: String,
        val develop: String
    )
}
