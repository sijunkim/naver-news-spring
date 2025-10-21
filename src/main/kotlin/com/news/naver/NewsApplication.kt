package com.news.naver

import com.news.naver.config.HealthChecker
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

/**
 * 네이버 뉴스 수집 및 슬랙 전송 애플리케이션의 메인 진입점입니다.
 * Spring Boot 애플리케이션을 초기화하고, 필요한 설정을 활성화합니다.
 *
 * `@SpringBootApplication`: Spring Boot 애플리케이션임을 선언합니다.
 * `@EnableConfigurationProperties`: `application.yml` 또는 `.env` 파일의 설정 값을 타입-세이프하게 주입받을 수 있도록 합니다.
 * `@EnableScheduling`: `@Scheduled` 어노테이션을 사용하여 스케줄링 기능을 활성화합니다.
 */
@SpringBootApplication
@EnableConfigurationProperties(
    AppProperties::class,
    NaverProperties::class,
    SlackProperties::class
)
@EnableScheduling
class NewsApplication {

    /**
     * 애플리케이션 시작 시 등록된 모든 `HealthChecker` 구현을 순차 실행합니다.
     * 하나의 `runBlocking` 스코프 안에서 DB, Redis 등 외부 저장소 연결 상태를 검증합니다.
     *
     * @param healthCheckers 스프링 컨텍스트에 등록된 헬스체크 구현 목록
     * @return 시작 시 실행되어 각 시스템의 헬스체크를 수행하는 `ApplicationRunner`
     */
    @Bean
    fun healthChecks(healthCheckers: List<HealthChecker>) = ApplicationRunner {
        runBlocking {
            healthCheckers.forEach { it.check() }
        }
    }
}

/**
 * Spring Boot 애플리케이션의 메인 함수입니다.
 * 애플리케이션을 실행합니다.
 *
 * @param args 커맨드 라인 인자
 */
fun main(args: Array<String>) {
    runApplication<NewsApplication>(*args)
}
