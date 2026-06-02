package com.news.naver.client

import com.news.naver.data.dto.llm.LLMRequest
import com.news.naver.data.dto.llm.LLMResponse
import com.news.naver.property.LLMProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class LLMClient(
    private val webClient: WebClient,
    private val llmProperties: LLMProperties
) {

    suspend fun post(request: LLMRequest): LLMResponse {
        val active = llmProperties.active
        return webClient.post()
            .uri(active.apiUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer ${active.apiKey}")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(LLMResponse::class.java)
            .awaitSingle()
    }
}
