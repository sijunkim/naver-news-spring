package com.news.naver.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

enum class DeliveryStatus {
    SUCCESS, RETRY, FAILED
}

@Table("delivery_log")
data class DeliveryLog(
    @Id
    val id: Long? = null,
    val articleId: Long,
    val channel: NewsChannel,
    val status: DeliveryStatus,
    val httpStatus: Int?,
    val sentAt: LocalDateTime = LocalDateTime.now(),
    val responseBody: String?
)
