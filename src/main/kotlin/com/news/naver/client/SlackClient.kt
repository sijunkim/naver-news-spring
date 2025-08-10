package com.news.naver.client

import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class SlackClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val webClient: WebClient

    init {
        val httpClient = HttpClient.create()
            .doOnConnected {
                it.addHandlerLast(ReadTimeoutHandler(10, TimeUnit.SECONDS))
                it.addHandlerLast(WriteTimeoutHandler(10, TimeUnit.SECONDS))
            }

        webClient = WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    suspend fun sendMessage(webhookUrl: String, message: String) {
        val payload = mapOf("text" to message)

        webClient.post()
            .uri(webhookUrl)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono<String>()
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).jitter(0.75))
            .doOnError { e -> logger.error("Slack 전송 실패: $message", e) }
            .onErrorResume { Mono.empty() } // 에러 발생 시에도 흐름이 끊기지 않도록 함
            .awaitSingle() // 코루틴 컨텍스트에서 실행
    }
}
