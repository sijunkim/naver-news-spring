package com.news.naver.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

enum class ExclusionScope {
    BREAKING, EXCLUSIVE, ALL
}

@Table("keyword_exclusion")
data class KeywordExclusion(
    @Id
    val id: Long? = null,
    val scope: ExclusionScope,
    val keyword: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
