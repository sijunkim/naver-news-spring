package com.news.naver.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * 뉴스 기사 정보를 나타내는 엔티티 클래스입니다.
 * `news_article` 테이블과 매핑됩니다.
 *
 * @property id 엔티티의 고유 식별자 (자동 증가)
 * @property naverLinkHash 네이버 링크의 SHA-256 해시 값 (중복 방지 키)
 * @property title 기사 제목
 * @property summary 기사 요약 (선택 사항)
 * @property companyId 언론사 ID (선택 사항)
 * @property publishedAt 기사 발행일시 (선택 사항)
 * @property fetchedAt 기사를 수집한 시간 (기본값: 현재 시간)
 * @property rawJson 원본 JSON 데이터 (선택 사항)
 */
@Table("news_article")
data class NewsArticleEntity(
    @Id
    @Column("id")
    val id: Long? = null,

    @Column("naver_link_hash")
    val naverLinkHash: String,

    @Column("naver_link")
    val naverLink: String,

    @Column("title")
    val title: String,

    @Column("summary")
    val summary: String?,

    @Column("company_id")
    val companyId: Long?,

    @Column("published_at")
    val publishedAt: LocalDateTime?,

    @Column("fetched_at")
    val fetchedAt: LocalDateTime = LocalDateTime.now(),

    @Column("raw_json")
    val rawJson: String?
)
