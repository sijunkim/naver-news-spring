package com.news.naver.data.dto.delivery

data class FilterStats(
    val timeFiltered: Int,
    val llmFiltered: Int,
    val ruleFiltered: Int,
    val spamFiltered: Int
)
