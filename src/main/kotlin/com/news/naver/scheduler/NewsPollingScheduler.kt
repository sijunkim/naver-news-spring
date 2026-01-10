package com.news.naver.scheduler

import com.news.naver.data.constant.StringConstants
import com.news.naver.data.enums.NewsChannel
import com.news.naver.property.AppProperties
import com.news.naver.service.NewsProcessingService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class NewsPollingScheduler(
    private val appProperties: AppProperties,
    private val service: NewsProcessingService,
    private val environment: Environment
) : SchedulingConfigurer {

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addFixedDelayTask(
            Runnable { poll() },
            Duration.ofSeconds(appProperties.poll.intervalSeconds)
        )
    }

    /**
     * 설정된 주기로 뉴스 폴링을 수행합니다.
     * 채널별 병렬 처리.
     * Dev/Prod 분기는 Spring Profile로 제어.
     */
    private fun poll() = runBlocking {
        val channelsToPoll = if (environment.activeProfiles.contains(StringConstants.PROFILE_LOCAL)) {
            listOf(NewsChannel.DEV)
        } else {
            listOf(NewsChannel.BREAKING, NewsChannel.EXCLUSIVE)
        }

        channelsToPoll.map { ch ->
            async { service.runOnce(ch) }
        }.awaitAll()
    }
}