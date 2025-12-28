package com.news.naver.data.dto.slack

data class SlackSendResult(
    val success: Boolean,
    val httpStatus: Int?,
    val body: String?
)
