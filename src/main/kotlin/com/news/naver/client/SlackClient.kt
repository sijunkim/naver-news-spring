package com.news.naver.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.news.naver.data.dto.slack.SlackSendResult
import com.news.naver.data.enums.NewsChannel
import com.news.naver.property.SlackProperties
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
class SlackClient(
    private val webClient: WebClient,
    private val slackProperties: SlackProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 지정된 뉴스 채널로 페이로드를 전송합니다.
     *
     * @param channel 뉴스 채널 (BREAKING, EXCLUSIVE, DEV)
     * @param payload 전송할 데이터 맵
     * @return SlackSendResult 전송 결과
     */
    suspend fun send(channel: NewsChannel, payload: Map<String, Any?>): SlackSendResult {
        val url = when (channel) {
            NewsChannel.BREAKING -> slackProperties.webhook.breaking
            NewsChannel.EXCLUSIVE -> slackProperties.webhook.exclusive
            NewsChannel.DEV -> slackProperties.webhook.develop
        }

        return try {
            val resp = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .toEntity(String::class.java)
                .awaitSingleOrNull()

            val code = resp?.statusCode?.value()
            SlackSendResult(
                success = (code != null && code in 200..299),
                httpStatus = code,
                body = resp?.body
            )
        } catch (e: WebClientResponseException) {
            // 400, 500 등 HTTP 에러 발생 시 payload 로깅
            val payloadJson = try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
            } catch (jsonEx: Exception) {
                "Failed to serialize payload: ${jsonEx.message}"
            }

            logger.error(
                "Slack webhook request failed. channel={} status={} url={}\nPayload:\n{}",
                channel.name,
                e.statusCode.value(),
                url,
                payloadJson,
                e
            )

            SlackSendResult(
                success = false,
                httpStatus = e.statusCode.value(),
                body = e.responseBodyAsString
            )
        } catch (e: Exception) {
            // 기타 예외 (네트워크 에러 등)
            val payloadJson = try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
            } catch (jsonEx: Exception) {
                "Failed to serialize payload: ${jsonEx.message}"
            }

            logger.error(
                "Slack webhook request failed with unexpected error. channel={} url={}\nPayload:\n{}",
                channel.name,
                url,
                payloadJson,
                e
            )

            SlackSendResult(
                success = false,
                httpStatus = null,
                body = e.message
            )
        }
    }
}
