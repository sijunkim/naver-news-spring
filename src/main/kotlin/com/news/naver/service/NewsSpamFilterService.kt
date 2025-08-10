package com.news.naver.service

import com.news.naver.domain.SpamKeywordLog
import com.news.naver.property.AppProperties
import com.news.naver.repository.SpamKeywordLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NewsSpamFilterService(
    private val spamKeywordLogRepository: SpamKeywordLogRepository,
    private val appProperties: AppProperties
) {

    suspend fun isSpam(newsTitle: String): Boolean {
        val keywords = extractKeywords(newsTitle)
        if (keywords.isEmpty()) return false

        val duplicatedCount = spamKeywordLogRepository.countByKeywordIn(keywords)
        return duplicatedCount > appProperties.duplicate.threshold
    }

    suspend fun recordKeywords(newsTitle: String) {
        val keywords = extractKeywords(newsTitle)
        val logs = keywords.map { SpamKeywordLog(keyword = it) }
        spamKeywordLogRepository.saveAll(logs).collect { /* consume the flow */ }
    }

    suspend fun cleanupOldKeywords() {
        // 2시간 이전의 키워드 로그 삭제
        val twoHoursAgo = LocalDateTime.now().minusHours(2)
        spamKeywordLogRepository.deleteAllByCreatedAtBefore(twoHoursAgo)
    }

    private fun extractKeywords(title: String): List<String> {
        return title.split(" ")
            .map { it.replace(Regex("\[(<b>)?(속보|단독)(</b>)?\]|"|"|`"), "").trim() }
            .filter { it.isNotBlank() }
    }
}
