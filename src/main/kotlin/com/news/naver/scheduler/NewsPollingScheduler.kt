package com.news.naver.scheduler

import com.news.naver.domain.NewsChannel
import com.news.naver.property.AppProperties
import com.news.naver.service.NewsProcessingService
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NewsPollingScheduler(
    private val processingService: NewsProcessingService,
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
}
