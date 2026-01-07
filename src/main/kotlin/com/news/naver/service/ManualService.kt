package com.news.naver.service

import com.news.naver.data.enums.NewsChannel
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import com.news.naver.repository.RuntimeStateRepository
import org.springframework.stereotype.Service

@Service
class ManualService(
    private val newsArticleRepository: NewsArticleRepository,
    private val newsProcessingService: NewsProcessingService,
    private val newsSpamFilterService: NewsSpamFilterService,
    private val runtimeStateRepository: RuntimeStateRepository,
    private val deliveryRepo: DeliveryLogRepository,
    private val metadataFetcher: com.news.naver.util.fetcher.WebsiteMetadataFetcher
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
        return newsSpamFilterService.resetKeywordCounters()
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

    suspend fun getDomain(domain: String): String? {
        return metadataFetcher.fetchPageTitle(domain)
    }
}
