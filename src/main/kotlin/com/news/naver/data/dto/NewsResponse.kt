package com.news.naver.data.dto

/**
 * Root data class representing the Naver News Open API response.
 */
data class NewsResponse(
    val lastBuildDate: String?,
    val total: Int?,
    val start: Int?,
    val display: Int?,
    val items: List<Item>?
)