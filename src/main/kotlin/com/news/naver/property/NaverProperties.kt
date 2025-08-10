package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "naver")
data class NaverProperties(
    val openapi: OpenApi
) {
    data class OpenApi(
        val url: String,
        val clientId: String,
        val clientSecret: String
    )
}
