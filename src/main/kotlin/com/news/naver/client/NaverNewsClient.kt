package com.news.naver.client

import com.news.naver.property.NaverProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class NaverNewsClient(private val naverProperties: NaverProperties) {

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(naverProperties.openapi.url)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
        .defaultHeader("X-Naver-Client-Id", naverProperties.openapi.clientId)
        .defaultHeader("X-Naver-Client-Secret", naverProperties.openapi.clientSecret)
        .build()

    suspend fun fetchNews(query: String): NaverNewsResponse {
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder.queryParam("query", query).build()
            }
            .retrieve()
            .awaitBody()
    }
}
