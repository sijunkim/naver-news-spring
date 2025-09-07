package com.news.naver.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig {

    @Bean
    fun webClient(builder: WebClient.Builder): WebClient {

        // 커넥션 풀 설정을 추가합니다.
        val provider = ConnectionProvider.builder("naver-news-provider")
            .maxConnections(50) // 최대 커넥션 수
            .pendingAcquireTimeout(Duration.ofSeconds(25)) // 커넥션 풀에서 커넥션을 얻기까지 대기 시간
            .maxIdleTime(Duration.ofSeconds(65)) // 65초 이상 유휴 상태인 커넥션은 풀에서 제거
            .build()

        val httpClient = HttpClient.create(provider) // 설정된 커넥션 풀로 HttpClient 생성
            .keepAlive(false)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 5초 내에 연결되지 않으면 타임아웃
            .responseTimeout(Duration.ofSeconds(10)) // 10초 내에 응답이 오지 않으면 타임아웃
            .followRedirect(true)
            .doOnConnected { conn ->
                conn
                    .addHandlerLast(ReadTimeoutHandler(10, TimeUnit.SECONDS)) // 10초 내에 읽기 작업이 없으면 타임아웃
                    .addHandlerLast(WriteTimeoutHandler(10, TimeUnit.SECONDS)) // 10초 내에 쓰기 작업이 없으면 타임아웃
            }

        return builder
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}