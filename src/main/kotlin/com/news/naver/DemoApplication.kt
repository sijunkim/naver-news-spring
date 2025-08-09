package com.news.naver

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.awaitOne

@SpringBootApplication
class DemoApplication {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun r2dbcInitializer(template: R2dbcEntityTemplate) = ApplicationRunner {
        runBlocking {
            try {
                val result = template.databaseClient.sql("SELECT 1").map { row -> row.get(0, Integer::class.java) }.awaitOne()
                logger.info("✅ Database connection successful, result: $result")
            } catch (e: Exception) {
                logger.error("❌ Database connection failed", e)
            }
        }
    }
}

fun main(args: Array<String>) {
	runApplication<DemoApplication>(*args)
}
