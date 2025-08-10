package com.news.naver.scheduler

import com.news.naver.domain.NewsChannel
import com.news.naver.property.AppProperties
import com.news.naver.service.NewsProcessingService
import com.news.naver.service.NewsSpamFilterService
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NewsPollingScheduler(
    private val processingService: NewsProcessingService,
    private val spamFilterService: NewsSpamFilterService,
    private val appProperties: AppProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRateString = "${app.poll.interval-seconds}000", initialDelay = 5000)
    fun pollBreakingNews() {
        runBlocking {
            launch {
                try {
                    processingService.processNews(NewsChannel.BREAKING)
                } catch (e: Exception) {
                    logger.error("Error during polling breaking news", e)
                }
            }
        }
    }

    @Scheduled(fixedRateString = "${app.poll.interval-seconds}000", initialDelay = 10000)
    fun pollExclusiveNews() {
        runBlocking {
            launch {
                try {
                    processingService.processNews(NewsChannel.EXCLUSIVE)
                } catch (e: Exception) {
                    logger.error("Error during polling exclusive news", e)
                }
            }
        }
    }

    @Scheduled(cron = "0 0 0/2 * * *") // Every 2 hours
    fun cleanupOldSpamKeywords() {
        runBlocking {
            launch {
                try {
                    logger.info("Running scheduled cleanup of old spam keywords.")
                    spamFilterService.cleanupOldKeywords()
                } catch (e: Exception) {
                    logger.error("Error during spam keyword cleanup", e)
                }
            }
        }
    }
}
