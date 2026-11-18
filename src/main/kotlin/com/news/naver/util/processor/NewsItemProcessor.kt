package com.news.naver.util.processor

import com.news.naver.client.NaverNewsClient
import com.news.naver.data.dto.Item
import com.news.naver.property.NaverProperties
import com.news.naver.service.LLMService
import com.news.naver.util.refiner.NewsRefinerService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class NewsItemProcessor(
    private val naverNewsClient: NaverNewsClient,
    private val llmService: LLMService,
    private val refiner: NewsRefinerService,
    private val naverProperties: NaverProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    internal val verdictCache = LlmVerdictCache()

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

    /**
     * 연예 뉴스를 걸러낸다. LLM 무료 한도를 아끼기 위해 3단계로 처리한다.
     *
     * 1. 네이버 링크가 연예 섹션(`entertain.naver.com`)이면 LLM 없이 제외한다.
     * 2. 이미 판정한 제목은 [verdictCache] 의 결과를 쓴다.
     * 3. 남은 제목만 LLM 에 묻고, 실제로 판정된 결과만 기억한다(호출 실패 폴백은 기억하지 않는다).
     */
    suspend fun filterByLLM(items: List<Item>): List<Item> {
        if (items.isEmpty()) {
            return emptyList()
        }

        val (entertainment, candidates) = items.partition { isEntertainmentLink(it.link) }
        if (entertainment.isNotEmpty()) {
            logger.info("Excluded ${entertainment.size} entertainment items by naver link (no LLM call)")
        }
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val titleOf = candidates.associateWith { refiner.refineTitle(it.title) }
        val unknownTitles = titleOf.values.distinct().filter { verdictCache.get(it) == null }

        val freshVerdicts: Map<String, Boolean> = if (unknownTitles.isEmpty()) {
            emptyMap()
        } else {
            val result = llmService.filterNewsTitles(unknownTitles)
            val keptSet = result.kept.toSet()
            unknownTitles.associateWith { it in keptSet }.also { verdicts ->
                if (result.judged) verdicts.forEach { (title, keep) -> verdictCache.put(title, keep) }
            }
        }

        return candidates.filter { item ->
            val title = titleOf.getValue(item)
            freshVerdicts[title] ?: verdictCache.get(title) ?: true
        }
    }

    /** 네이버 연예 섹션 기사인지. m.entertain.naver.com / entertain.naver.com 만 해당한다. */
    internal fun isEntertainmentLink(link: String?): Boolean {
        val host = link?.let { runCatching { URI(it.trim()).host }.getOrNull() }?.lowercase() ?: return false
        return host in ENTERTAINMENT_HOSTS
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

    private companion object {
        val ENTERTAINMENT_HOSTS = setOf("m.entertain.naver.com", "entertain.naver.com")
    }
}
