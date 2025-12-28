package com.news.naver.data.dto.delivery

import com.news.naver.data.enum.NewsChannel

data class NewsDeliveryResult(
    val channel: NewsChannel,
    val totalFetched: Int,
    val filtered: FilterStats,
    val delivered: List<DeliveredNews>,
    val failed: List<FailedNews>
)
