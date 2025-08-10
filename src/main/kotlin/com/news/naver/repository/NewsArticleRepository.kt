package com.news.naver.repository

import com.news.naver.domain.NewsArticle
import kotlinx.coroutines.flow.toList
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface NewsArticleRepository : CoroutineCrudRepository<NewsArticle, Long> {
    suspend fun existsByNaverLinkHash(hash: String): Boolean

    suspend fun findTopByOrderByPublishedAtDesc(): NewsArticle? {
        // R2DBC does not support findTop... directly, so we emulate it.
        // This is not efficient for large datasets, but acceptable for this use case.
        return findAll().toList().maxByOrNull { it.publishedAt!! }
    }
}
