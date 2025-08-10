package com.news.naver.entity

import com.news.naver.data.enum.DeliveryStatus
import com.news.naver.data.enum.NewsChannel
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * 뉴스 전송 로그를 나타내는 엔티티 클래스입니다.
 * `delivery_log` 테이블과 매핑됩니다.
 *
 * @property id 엔티티의 고유 식별자 (자동 증가)
 * @property articleId 전송된 뉴스 기사의 ID
 * @property channel 뉴스가 전송된 채널 (BREAKING, EXCLUSIVE, DEV)
 * @property status 전송 상태 (SUCCESS, RETRY, FAILED)
 * @property httpStatus HTTP 응답 상태 코드 (선택 사항)
 * @property sentAt 전송된 시간 (기본값: 현재 시간)
 * @property responseBody 응답 본문 (선택 사항)
 */
@Table("delivery_log")
data class DeliveryLogEntity(
    @Id
    val id: Long? = null,
    val articleId: Long,
    val channel: NewsChannel,
    val status: DeliveryStatus,
    val httpStatus: Int?,
    val sentAt: LocalDateTime = LocalDateTime.now(),
    val responseBody: String?
)
