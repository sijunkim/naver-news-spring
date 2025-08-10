package com.news.naver.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("spam_keyword_log")
data class SpamKeywordLog(
    @Id
    val id: Long? = null,
    val keyword: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
