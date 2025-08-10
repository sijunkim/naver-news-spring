package com.news.naver.repository

import com.news.naver.entity.NewsArticleEntity

interface NewsArticleRepositoryCustom {
    suspend fun existsByNaverLinkHash(hash: String): Boolean
    suspend fun findTopByOrderByPublishedAtDesc(): NewsArticleEntity?
}
