package com.news.naver.data.dto.delivery

data class FilterStats(
    val timeFiltered: Int,
    val chatGptFiltered: Int,
    val ruleFiltered: Int,
    val spamFiltered: Int
)
