package com.news.naver.scheduler

import com.news.naver.data.constant.StringConstants
import com.news.naver.data.enums.NewsChannel
import com.news.naver.service.NewsProcessingService
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NewsPollingScheduler(
    private val service: NewsProcessingService,
    private val environment: Environment
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.error("Uncaught exception in NewsPollingScheduler coroutine", throwable)
    }

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("NewsPolling") + exceptionHandler
    )

    /**
     * 매분 0초에 뉴스 폴링을 수행합니다.
     * 채널별 병렬 처리.
     * Dev/Prod 분기는 Spring Profile로 제어.
     */
    @Scheduled(cron = "0 * * * * *")
    fun poll() {
        scope.launch {
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

    @PreDestroy
    fun cleanup() {
        logger.info("Shutting down NewsPollingScheduler coroutine scope")
        scope.cancel()
    }
}