package com.news.naver.service

import com.news.naver.data.enum.NewsChannel
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import com.news.naver.repository.RuntimeStateRepository
import com.news.naver.repository.SpamKeywordLogRepository
import org.springframework.stereotype.Service

@Service
class ManualService(
    private val newsArticleRepository: NewsArticleRepository,
    private val newsProcessingService: NewsProcessingService,
    private val spamKeywordLogRepository: SpamKeywordLogRepository,
    private val runtimeStateRepository: RuntimeStateRepository,
    private val deliveryRepo: DeliveryLogRepository,
) {

    suspend fun runDevNewsPoll() {
        newsProcessingService.runOnce(NewsChannel.DEV)
    }

    suspend fun runBreakingNewsPoll() {
        newsProcessingService.runOnce(NewsChannel.BREAKING)
    }

    suspend fun runExclusiveNewsPoll() {
        newsProcessingService.runOnce(NewsChannel.EXCLUSIVE)
    }

    suspend fun resetSpamKeywords(): Long {
        return spamKeywordLogRepository.deleteAll()
    }

    suspend fun deletePollTimestamps(): Long {
        return runtimeStateRepository.deleteByKeyStartingWith("last_poll_time")
    }

    suspend fun deleteNewsArticles(): Long {
        return newsArticleRepository.deleteAll()
    }

    suspend fun deleteDeliveryLogs(): Long {
        return deliveryRepo.deleteAll()
    }

    suspend fun resetAllData() {
        deleteNewsArticles()
        resetSpamKeywords()
        deletePollTimestamps()
        deleteDeliveryLogs()
    }
}