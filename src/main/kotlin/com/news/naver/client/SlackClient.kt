package com.news.naver.client

import com.news.naver.data.dto.slack.SlackSendResult
import com.news.naver.data.enum.NewsChannel
import com.news.naver.property.SlackProperties
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

@Component
class SlackClient(
    private val webClient: WebClient,
    private val props: SlackProperties
) {
    suspend fun send(channel: NewsChannel, payload: Map<String, Any?>): SlackSendResult {
        val url = props.urlFor(channel)

        val resp = webClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(payload))
            .retrieve()
            .toEntity(String::class.java)
            .awaitSingleOrNull()

        val code = resp?.statusCode?.value()
        return SlackSendResult(
            success = (code != null && code in 200..299),
            httpStatus = code,
            body = resp?.body
        )
    }
}
