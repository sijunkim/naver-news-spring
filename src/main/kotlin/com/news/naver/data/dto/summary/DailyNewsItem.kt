package com.news.naver.data.dto.summary

import java.time.LocalDateTime

data class DailyNewsItem(
    val articleId: Long,
    val title: String,
    val summary: String?,
    val firstSentAt: LocalDateTime,
    val channels: String
)
