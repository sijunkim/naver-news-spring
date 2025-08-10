package com.news.naver.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * 제외할 언론사 정보를 나타내는 엔티티 클래스입니다.
 * `press_exclusion` 테이블과 매핑됩니다.
 *
 * @property id 엔티티의 고유 식별자 (자동 증가)
 * @property pressName 제외할 언론사 이름
 * @property createdAt 레코드가 생성된 시간 (기본값: 현재 시간)
 */
@Table("press_exclusion")
data class PressExclusionEntity(
    @Id
    val id: Long? = null,
    val pressName: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
