package com.news.naver.client

import com.news.naver.data.dto.chatgpt.ChatGPTRequest
import com.news.naver.data.dto.chatgpt.ChatGPTResponse
import com.news.naver.property.ChatGPTProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ChatGPTClient(
    private val webClient: WebClient,
    private val chatGPTProperties: ChatGPTProperties
) {

    suspend fun post(request: ChatGPTRequest): ChatGPTResponse {
        return webClient.post()
            .uri(chatGPTProperties.apiUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer ${chatGPTProperties.apiKey}")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatGPTResponse::class.java)
            .awaitSingle()
    }
}
