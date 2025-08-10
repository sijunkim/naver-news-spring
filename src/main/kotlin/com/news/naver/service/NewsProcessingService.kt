package com.news.naver.service

import com.news.naver.client.NaverNewsClient
import com.news.naver.client.NaverNewsResponse
import com.news.naver.client.SlackClient
import com.news.naver.common.HashUtils
import com.news.naver.domain.DeliveryLog
import com.news.naver.domain.DeliveryStatus
import com.news.naver.domain.NewsArticle
import com.news.naver.domain.NewsChannel
import com.news.naver.property.SlackProperties
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URL
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class NewsProcessingService(
    private val naverNewsClient: NaverNewsClient,
    private val slackClient: SlackClient,
    private val newsFilterService: NewsFilterService,
    private val newsRefinerService: NewsRefinerService,
    private val newsSpamFilterService: NewsSpamFilterService,
    private val newsArticleRepository: NewsArticleRepository,
    private val deliveryLogRepository: DeliveryLogRepository,
    private val slackProperties: SlackProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun processNews(channel: NewsChannel) = coroutineScope {
        logger.info("Start processing news for channel: ${channel.name}")

        val lastArticleTime = newsArticleRepository.findTopByOrderByPublishedAtDesc()?.publishedAt ?: LocalDateTime.now().minusDays(1)
        val excludedKeywords = newsFilterService.getExcludedKeywords(channel)
        val excludedPresses = newsFilterService.getExcludedPresses()

        val newsItems = naverNewsClient.fetchNews(channel.query).channel.items

        newsItems.asFlow()
            .map { item -> Triple(item, parseDate(item.pubDate), normalizeUrl(item.originalLink)) }
            .filter { (_, pubDate, _) -> pubDate.isAfter(lastArticleTime) } // 1. 마지막 수신 시간보다 새로운 뉴스만
            .map { (item, pubDate, normalizedUrl) ->
                val hash = HashUtils.sha256(normalizedUrl)
                quadruple(item, pubDate, normalizedUrl, hash)
            }
            .filter { (_, _, _, hash) -> !newsArticleRepository.existsByNaverLinkHash(hash) } // 2. DB에 없는 새로운 뉴스만 (해시 기준)
            .filter { (item, _, _, _) -> !newsSpamFilterService.isSpam(item.title) } // 3. 스팸 키워드 필터링
            .filter { (item, _, _, _) -> !newsFilterService.filter(item, excludedKeywords, excludedPresses) } // 4. 제외 키워드/언론사 필터링
            .map { (item, pubDate, normalizedUrl, hash) -> createNewsArticle(item, pubDate, normalizedUrl, hash) }
            .toList()
            .forEach { article ->
                launch {
                    val savedArticle = newsArticleRepository.save(article)
                    logger.info("New article saved: ${savedArticle.title}")

                    newsSpamFilterService.recordKeywords(savedArticle.title) // 스팸 필터링을 위해 키워드 기록

                    val payload = newsRefinerService.createSlackPayload(savedArticle, channel)
                    val webhookUrl = getWebhookUrl(channel)
                    slackClient.sendMessage(webhookUrl, payload)

                    deliveryLogRepository.save(
                        DeliveryLog(
                            articleId = savedArticle.id!!,
                            channel = channel,
                            status = DeliveryStatus.SUCCESS,
                            httpStatus = 200,
                            responseBody = "OK"
                        )
                    )
                }
            }
        logger.info("Finished processing news for channel: ${channel.name}")
    }

    private fun normalizeUrl(url: String): String {
        val parsedUrl = URL(url)
        return "${parsedUrl.protocol}://${parsedUrl.host}${parsedUrl.path}"
    }

    private suspend fun createNewsArticle(item: NaverNewsResponse.Item, pubDate: LocalDateTime, normalizedUrl: String, hash: String): NewsArticle {
        return NewsArticle(
            naverLinkHash = hash,
            title = newsRefinerService.refineHtml(item.title),
            summary = newsRefinerService.refineHtml(item.description),
            press = newsRefinerService.extractPress(item.originalLink),
            publishedAt = pubDate,
            rawJson = item.toString()
        )
    }

    private fun parseDate(pubDate: String): LocalDateTime {
        return try {
            ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime()
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    }

    private fun getWebhookUrl(channel: NewsChannel): String {
        return when (channel) {
            NewsChannel.BREAKING -> slackProperties.webhook.breaking
            NewsChannel.EXCLUSIVE -> slackProperties.webhook.exclusive
            NewsChannel.DEV -> slackProperties.webhook.develop
        }
    }
}

fun <A, B, C, D> quadruple(a: A, b: B, c: C, d: D): Quadruple<A, B, C, D> = Quadruple(a, b, c, d)
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// NewsArticleRepository 에 추가되어야 할 메소드
suspend fun NewsArticleRepository.findTopByOrderByPublishedAtDesc(): NewsArticle? = this.findAll().toList().maxByOrNull { it.publishedAt!! }
