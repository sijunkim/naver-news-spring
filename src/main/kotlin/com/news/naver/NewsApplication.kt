package com.news.naver

import com.news.naver.config.DatabaseHealthChecker
import com.news.naver.property.AppProperties
import com.news.naver.property.NaverProperties
import com.news.naver.property.SlackProperties
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(
    AppProperties::class,
    NaverProperties::class,
    SlackProperties::class
)
@EnableScheduling
class NewsApplication {

    @Bean
    fun databaseHealthCheck(healthChecker: DatabaseHealthChecker) = ApplicationRunner {
        runBlocking {
            healthChecker.check()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<NewsApplication>(*args)
}
