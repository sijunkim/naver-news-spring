package com.news.naver.entity

import com.news.naver.data.enums.ExclusionScope
import com.news.naver.util.DateTimeUtils
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * 제외할 키워드 정보를 나타내는 엔티티 클래스입니다.
 * `keyword_exclusion` 테이블과 매핑됩니다.
 *
 * @property id 엔티티의 고유 식별자 (자동 증가)
 * @property scope 키워드 제외가 적용될 범위 (BREAKING, EXCLUSIVE, ALL)
 * @property keyword 제외할 키워드
 * @property createdAt 레코드가 생성된 시간 (기본값: 현재 시간)
 */
@Table("keyword_exclusion")
data class KeywordExclusionEntity(
    @Id
    val id: Long? = null,
    val scope: ExclusionScope,
    val keyword: String,
    val createdAt: LocalDateTime = DateTimeUtils.now()
)

