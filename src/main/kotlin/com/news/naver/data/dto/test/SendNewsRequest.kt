package com.news.naver.data.dto.test

data class SendNewsRequest(
    val keyword: String, // "속보" 또는 "단독"
    val maxItems: Int? = 10
)
