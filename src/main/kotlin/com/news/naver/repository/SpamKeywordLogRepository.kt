package com.news.naver.repository

import com.news.naver.domain.SpamKeywordLog
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface SpamKeywordLogRepository : CoroutineCrudRepository<SpamKeywordLog, Long> {

    @Query("SELECT COUNT(*) FROM spam_keyword_log WHERE keyword IN (:keywords)")
    suspend fun countByKeywordIn(keywords: List<String>): Long

    suspend fun deleteAllByCreatedAtBefore(dateTime: LocalDateTime)
}
