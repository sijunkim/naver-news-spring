package com.news.naver.scheduler

import com.news.naver.service.DailySummaryService
import com.news.naver.util.DateTimeUtils
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DailySummaryScheduler(
    private val dailySummaryService: DailySummaryService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.error("Uncaught exception in DailySummaryScheduler coroutine", throwable)
    }

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("DailySummary") + exceptionHandler
    )

    /**
     * 매일 00:01에 실행됩니다.
     * 전날(어제) 발송된 뉴스를 요약하여 Slack으로 전송합니다.
     */
    @Scheduled(cron = "0 1 0 * * *")
    fun generateAndSendDailySummary() {
        val yesterday = DateTimeUtils.today().minusDays(1)
        logger.info("Daily summary scheduler triggered at 00:01 for date: $yesterday")

        scope.launch {
            try {
                dailySummaryService.generateAndSendDailySummary(yesterday)
            } catch (e: Exception) {
                logger.error("Failed to generate and send daily summary for $yesterday", e)
            }
        }
    }

    @PreDestroy
    fun cleanup() {
        logger.info("Shutting down DailySummaryScheduler coroutine scope")
        scope.cancel()
    }
}
