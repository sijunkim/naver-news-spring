package com.news.naver.scheduler

import com.news.naver.property.AppProperties
import com.news.naver.service.DailySummaryService
import com.news.naver.util.DateTimeUtils
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DailySummaryScheduler(
    private val dailySummaryService: DailySummaryService,
    private val appProperties: AppProperties
) : SchedulingConfigurer {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addCronTask(
            Runnable { generateAndSendDailySummary() },
            appProperties.dailySummary.cron
        )
    }

    /**
     * 설정된 스케줄에 따라 실행됩니다 (기본값: 매일 자정 00:00)
     * 전날(어제) 발송된 뉴스를 요약하여 Slack으로 전송합니다
     */
    private fun generateAndSendDailySummary() = runBlocking {
        val yesterday = DateTimeUtils.today().minusDays(1)

        logger.info("Daily summary scheduler triggered at midnight for date: $yesterday")

        try {
            dailySummaryService.generateAndSendDailySummary(yesterday)
        } catch (e: Exception) {
            logger.error("Failed to generate and send daily summary for $yesterday", e)
        }
    }
}
