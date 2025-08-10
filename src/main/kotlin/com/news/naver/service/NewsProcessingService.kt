package com.news.naver.service

import com.news.naver.client.NaverNewsClient
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
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class NewsProcessingService(
    private val naverNewsClient: NaverNewsClient,
    private val slackClient: SlackClient,
    private val newsFilterService: NewsFilterService,
    private val newsArticleRepository: NewsArticleRepository,
    private val deliveryLogRepository: DeliveryLogRepository,
    private val slackProperties: SlackProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun processNews(channel: NewsChannel) = coroutineScope {
        logger.info("Start processing news for channel: ${channel.name}")

        val excludedKeywords = newsFilterService.getExcludedKeywords(channel)
        val excludedPresses = newsFilterService.getExcludedPresses()

        val newsItems = naverNewsClient.fetchNews(channel.query).channel.items

        newsItems.asFlow()
            .map { item -> Triple(item, normalizeUrl(item.originalLink), HashUtils.sha256(normalizeUrl(item.originalLink))) }
            .filter { (_, _, hash) -> !newsArticleRepository.existsByNaverLinkHash(hash) }
            .filter { (item, _, _) -> !newsFilterService.filter(item, excludedKeywords, excludedPresses) }
            .map { (item, normalizedUrl, hash) -> createNewsArticle(item, normalizedUrl, hash) }
            .toList()
            .forEach { article ->
                launch {
                    val savedArticle = newsArticleRepository.save(article)
                    logger.info("New article saved: ${savedArticle.title}")
                    val webhookUrl = getWebhookUrl(channel)
                    slackClient.sendMessage(webhookUrl, "[${channel.name}] ${savedArticle.title}\n${savedArticle.naverLinkHash}")
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

    private fun createNewsArticle(item: com.news.naver.client.NaverNewsResponse.Item, normalizedUrl: String, hash: String): NewsArticle {
        val pubDate = try {
            LocalDateTime.parse(item.pubDate, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH))
        } catch (e: Exception) {
            null
        }
        return NewsArticle(
            naverLinkHash = hash,
            title = item.title.replace(Regex("<[^"]*>"), ""),
            summary = item.description.replace(Regex("<[^"]*>"), ""),
            press = extractPress(item.originalLink),
            publishedAt = pubDate,
            rawJson = item.toString() // For simplicity, storing string representation
        )
    }

    private fun extractPress(originalLink: String): String? {
        // A simple way to extract press name from URL, might need improvement
        return try {
            URL(originalLink).host.split(".").dropLast(1).last()
        } catch (e: Exception) {
            null
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