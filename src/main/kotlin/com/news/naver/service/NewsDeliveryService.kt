package com.news.naver.service

import com.news.naver.client.SlackClient
import com.news.naver.data.dto.Item
import com.news.naver.data.dto.News
import com.news.naver.data.dto.delivery.DeliveredNews
import com.news.naver.data.dto.delivery.FailedNews
import com.news.naver.data.dto.delivery.FilterStats
import com.news.naver.data.dto.delivery.NewsDeliveryResult
import com.news.naver.data.enums.NewsChannel
import com.news.naver.util.DateTimeUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 뉴스 전송 서비스 (DB 저장 없는 순수 비즈니스 로직)
 *
 * 이 서비스는 인프라스트럭처(DB, Redis)에 의존하지 않습니다.
 * 테스트 및 재사용 가능한 핵심 비즈니스 로직만 포함합니다.
 */
@Service
class NewsDeliveryService(
    private val slack: SlackClient,
    private val refiner: com.news.naver.util.refiner.NewsRefinerService,
    private val filter: NewsFilterService,
    private val spam: NewsSpamFilterService,
    private val newsCompanyService: NewsCompanyService,
    private val itemProcessor: com.news.naver.util.processor.NewsItemProcessor,
    private val slackFormatter: com.news.naver.util.formatter.SlackMessageFormatter
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 뉴스를 가져와서 필터링 후 슬랙으로 전송합니다 (DB 저장 없음)
     *
     * @param channel 뉴스 채널
     * @param query 검색 쿼리 (null이면 channel의 기본 query 사용)
     * @param maxItems 최대 전송 개수 (null이면 제한 없음)
     * @param lastPollTime 마지막 폴링 시간 (null이면 시간 필터링 안함)
     * @return 전송 결과
     */
    suspend fun deliverNews(
        channel: NewsChannel,
        query: String? = null,
        maxItems: Int? = null,
        lastPollTime: LocalDateTime? = null
    ): NewsDeliveryResult {
        // 1. 뉴스 가져오기
        val searchQuery = query ?: channel.query
        val items = itemProcessor.fetchItems(searchQuery)

        // 2. 시간 필터링
        val timeFilteredItems = itemProcessor.filterByTime(items, lastPollTime)
        val timeFilteredCount = items.size - timeFilteredItems.size

        // 3. ChatGPT 필터링
        val chatGptFilteredItems = itemProcessor.filterByChatGPT(timeFilteredItems)
        val chatGptFilteredCount = timeFilteredItems.size - chatGptFilteredItems.size

        // 4. 최대 개수 제한
        val limitedItems = if (maxItems != null && maxItems > 0) {
            chatGptFilteredItems.take(maxItems)
        } else {
            chatGptFilteredItems
        }

        // 5. 뉴스 전송 (병렬 처리)
        val deliveryResults = deliverNewsItems(channel, limitedItems)

        // 6. 결과 집계
        val delivered = mutableListOf<DeliveredNews>()
        val failed = mutableListOf<FailedNews>()
        var ruleFilteredCount = 0
        var spamFilteredCount = 0

        deliveryResults.forEach { result ->
            when (result) {
                is ItemDeliveryResult.Success -> delivered.add(
                    DeliveredNews(
                        title = result.title,
                        link = result.link,
                        company = result.company,
                        pubDate = result.pubDate
                    )
                )
                is ItemDeliveryResult.FilteredByRule -> {
                    ruleFilteredCount++
                    failed.add(FailedNews(title = result.title, reason = "Filtered by exclusion rule"))
                }
                is ItemDeliveryResult.FilteredBySpam -> {
                    spamFilteredCount++
                    failed.add(FailedNews(title = result.title, reason = "Filtered by spam detection"))
                }
                is ItemDeliveryResult.Failed -> failed.add(
                    FailedNews(title = result.title, reason = result.reason)
                )
            }
        }

        return NewsDeliveryResult(
            channel = channel,
            totalFetched = items.size,
            filtered = FilterStats(
                timeFiltered = timeFilteredCount,
                chatGptFiltered = chatGptFilteredCount,
                ruleFiltered = ruleFilteredCount,
                spamFiltered = spamFilteredCount
            ),
            delivered = delivered,
            failed = failed
        )
    }

    /**
     * 임의의 메시지를 슬랙으로 전송합니다 (테스트용)
     */
    suspend fun sendTestMessage(channel: NewsChannel, message: String): Boolean {
        val now = DateTimeUtils.nowZoned()
        val formattedDate = slackFormatter.formatKoreanDateTime(
            now.format(DateTimeFormatter.RFC_1123_DATE_TIME)
        )

        val payload = mapOf(
            "text" to message,
            "blocks" to listOf(
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "mrkdwn",
                        "text" to "*$message*"
                    )
                ),
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "plain_text",
                        "text" to "내용없음",
                        "emoji" to true
                    )
                ),
                mapOf(
                    "type" to "context",
                    "elements" to listOf(
                        mapOf(
                            "type" to "plain_text",
                            "text" to "$formattedDate | 테스트일보"
                        )
                    )
                ),
                mapOf(
                    "type" to "divider"
                )
            )
        )

        val result = slack.send(channel, payload)
        return result.success
    }


    private suspend fun deliverNewsItems(
        channel: NewsChannel,
        items: List<Item>
    ): List<ItemDeliveryResult> {
        if (items.isEmpty()) {
            return emptyList()
        }

        return coroutineScope {
            items.map { item ->
                async { deliverSingleItem(channel, item) }
            }.awaitAll()
        }
    }

    private suspend fun deliverSingleItem(
        channel: NewsChannel,
        item: Item
    ): ItemDeliveryResult {
        val title = refiner.refineTitle(item.title)
        val description = refiner.refineDescription(item.description)

        // 1. 회사 정보 추출
        val companyDomain = refiner.extractCompany(item.link)
        if (companyDomain == null) {
            logger.warn("Unable to extract company from link: ${item.link}")
            return ItemDeliveryResult.Failed(title, "Unable to extract company")
        }
        val company = newsCompanyService.findOrCreateCompany(companyDomain)

        // 2. 제외 룰 검사
        if (filter.isExcluded(title, company.name, channel)) {
            logger.debug("Article excluded by rule: $title")
            return ItemDeliveryResult.FilteredByRule(title)
        }

        // 3. 스팸 검사
        if (spam.isSpamByTitleTokens(title)) {
            logger.debug("Article classified as spam: $title")
            return ItemDeliveryResult.FilteredBySpam(title)
        }

        // 4. 슬랙 전송
        val news = News(
            title = title,
            originalLink = item.link,
            link = item.link,
            company = company.name,
            description = description,
            pubDate = item.pubDate
        )
        val payload = slackFormatter.createPayload(news)
        val sendResult = slack.send(channel, payload)

        return if (sendResult.success) {
            ItemDeliveryResult.Success(
                title = title,
                link = item.link,
                company = company.name,
                pubDate = item.pubDate
            )
        } else {
            ItemDeliveryResult.Failed(
                title = title,
                reason = "Slack delivery failed: ${sendResult.httpStatus}"
            )
        }
    }


    private sealed class ItemDeliveryResult {
        data class Success(
            val title: String,
            val link: String?,
            val company: String,
            val pubDate: String?
        ) : ItemDeliveryResult()

        data class FilteredByRule(val title: String) : ItemDeliveryResult()
        data class FilteredBySpam(val title: String) : ItemDeliveryResult()
        data class Failed(val title: String, val reason: String) : ItemDeliveryResult()
    }
}
