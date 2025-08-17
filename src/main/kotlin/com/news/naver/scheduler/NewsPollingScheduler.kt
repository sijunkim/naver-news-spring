package com.news.naver.scheduler

import com.news.naver.data.enum.NewsChannel
import com.news.naver.property.AppProperties
import com.news.naver.service.NewsProcessingService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NewsPollingScheduler(
    private val props: AppProperties,
    private val service: NewsProcessingService,
    private val environment: Environment
) {
    /**
     * 기본 60초 주기. 채널별 병렬 처리.
     * Dev/Prod 분기는 Spring Profile로 제어하는 것을 권장.
     */
    @Scheduled(fixedDelayString = "\${app.poll.intervalSeconds:60}000")
    fun poll() = runBlocking {
        val channelsToPoll = if (environment.activeProfiles.contains("local")) {
            listOf(NewsChannel.DEV)
        } else {
            listOf(NewsChannel.BREAKING, NewsChannel.EXCLUSIVE)
        }

        channelsToPoll.map { ch ->
            async { service.runOnce(ch) }
        }.awaitAll()
    }
}