package com.news.naver.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("news_article")
data class NewsArticle(
    @Id
    val id: Long? = null,
    val naverLinkHash: String,
    val title: String,
    val summary: String?,
    val press: String?,
    val publishedAt: LocalDateTime?,
    val fetchedAt: LocalDateTime = LocalDateTime.now(),
    val rawJson: String?
)
