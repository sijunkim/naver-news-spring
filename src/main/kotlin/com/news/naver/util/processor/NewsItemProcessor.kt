package com.news.naver.util.processor

import com.news.naver.client.NaverNewsClient
import com.news.naver.data.dto.Item
import com.news.naver.property.NaverProperties
import com.news.naver.service.ChatGPTService
import com.news.naver.util.refiner.NewsRefinerService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class NewsItemProcessor(
    private val naverNewsClient: NaverNewsClient,
    private val chatGPTService: ChatGPTService,
    private val refiner: NewsRefinerService,
    private val naverProperties: NaverProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun fetchItems(query: String): List<Item> {
        val resp = naverNewsClient.search(
            query = query,
            display = naverProperties.search.display,
            start = naverProperties.search.start,
            sort = naverProperties.search.sort
        )
        return resp.items
            ?.filter { it.title?.contains(query) ?: false }
            .orEmpty()
    }

    fun filterByTime(
        items: List<Item>,
        lastPollTime: LocalDateTime?
    ): List<Item> {
        if (lastPollTime == null) {
            return items
        }

        return items.filter { item ->
            val publishedAt = parsePubDate(item.pubDate)
            publishedAt?.isAfter(lastPollTime) ?: false
        }
    }

    suspend fun filterByChatGPT(items: List<Item>): List<Item> {
        if (items.isEmpty()) {
            return emptyList()
        }

        val titles = items.map { refiner.refineTitle(it.title) }
        val filteredTitles = chatGPTService.filterNewsTitles(titles)
        val filteredTitleSet = filteredTitles.toSet()

        return items.filter { item ->
            val refinedTitle = refiner.refineTitle(item.title)
            filteredTitleSet.contains(refinedTitle)
        }
    }

    fun parsePubDate(pubDate: String?): LocalDateTime? {
        return refiner.pubDateToKst(pubDate)?.let {
            try {
                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            } catch (e: Exception) {
                logger.error("Failed to parse item pubDate: $it", e)
                null
            }
        }
    }
}
