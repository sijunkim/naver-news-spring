package com.news.naver.client

import com.news.naver.data.dto.NewsResponse
import com.news.naver.property.NaverProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets

@Component
class NaverNewsClient(
    private val webClient: WebClient,
    private val props: NaverProperties,
) {
    /**
     * 네이버 뉴스 검색
     * @param query  검색어(예: "속보", "단독")
     * @param display  페이지당 건수(기본 30)
     * @param start    시작 인덱스(기본 1)
     * @param sort     정렬(sim|date) – 기본 date
     */
    suspend fun search(
        query: String,
        display: Int = 30,
        start: Int = 1,
        sort: String = "date"
    ): NewsResponse {
        val uri = UriComponentsBuilder.fromUriString(props.openapi.url)
            .queryParam("query", query)
            .queryParam("display", display)
            .queryParam("start", start)
            .queryParam("sort", sort)
            .build()
            .encode(StandardCharsets.UTF_8)
            .toUri()

        return webClient.get()
            .uri(uri)
            .accept(MediaType.APPLICATION_JSON)
            .header("X-Naver-Client-Id", props.openapi.clientId)
            .header("X-Naver-Client-Secret", props.openapi.clientSecret)
            .retrieve()
            .bodyToMono(NewsResponse::class.java)
            .awaitSingle()
    }
}