package com.news.naver.data.dto.test

data class SendNewsByChannelRequest(
    val channel: String, // "BREAKING", "EXCLUSIVE", "DEV"
    val maxItems: Int? = null
)
