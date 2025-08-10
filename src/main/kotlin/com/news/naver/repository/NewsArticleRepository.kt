package com.news.naver.repository

import com.news.naver.domain.NewsArticle
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface NewsArticleRepository : CoroutineCrudRepository<NewsArticle, Long> {
    suspend fun existsByNaverLinkHash(hash: String): Boolean
}
