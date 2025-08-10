package com.news.naver.config

import org.slf4j.LoggerFactory
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.awaitOne
import org.springframework.stereotype.Component

@Component
class DatabaseHealthChecker(private val template: R2dbcEntityTemplate) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun check() {
        try {
            val result = template.databaseClient.sql("SELECT 1").map { row -> row.get(0, Integer::class.java) }.awaitOne()
            logger.info("✅ Database connection successful, result: $result")
        } catch (e: Exception) {
            logger.error("❌ Database connection failed", e)
            // 실패 시 애플리케이션을 종료하거나, 재시도 로직을 추가할 수 있습니다.
            throw e // 예외를 다시 던져서 애플리케이션 시작을 중단시킬 수 있습니다.
        }
    }
}
