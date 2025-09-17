package com.news.naver.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * 스팸 키워드 로그를 나타내는 엔티티 클래스입니다.
 * `spam_keyword_log` 테이블과 매핑되며, 뉴스 제목에서 추출된 키워드와 생성 시간을 기록합니다.
 *
 * @property id 엔티티의 고유 식별자 (자동 증가)
 * @property keyword 뉴스 제목에서 추출된 키워드
 * @property createdAt 로그가 생성된 시간 (기본값: 현재 시간)
 */
@Table("spam_keyword_log")
data class SpamKeywordLogEntity(
    @Id
    val id: Long? = null,
    val keyword: String,
    var count: Int = 1,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime? = null
)
