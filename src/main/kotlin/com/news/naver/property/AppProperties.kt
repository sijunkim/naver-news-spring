package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val poll: Poll,
    val duplicate: Duplicate
) {
    data class Poll(
        val intervalSeconds: Long
    )

    data class Duplicate(
        val threshold: Int
    )
}
