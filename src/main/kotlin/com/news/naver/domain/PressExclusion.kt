package com.news.naver.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("press_exclusion")
data class PressExclusion(
    @Id
    val id: Long? = null,
    val pressName: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
