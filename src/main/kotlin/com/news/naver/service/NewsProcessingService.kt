package com.news.naver.service

import com.news.naver.client.NaverNewsClient
import com.news.naver.client.SlackClient
import com.news.naver.common.HashUtils
import com.news.naver.data.dto.Item
import com.news.naver.data.dto.News
import com.news.naver.data.enum.NewsChannel
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import com.news.naver.repository.RuntimeStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import net.logstash.logback.marker.Markers.append
import net.logstash.logback.marker.Markers.appendEntries

@Service
class NewsProcessingService(
    private val naverNewsClient: NaverNewsClient,
    private val slack: SlackClient,
    private val refiner: NewsRefinerService,
    private val filter: NewsFilterService,
    private val spam: NewsSpamFilterService,
    private val articleRepo: NewsArticleRepository,
    private val deliveryRepo: DeliveryLogRepository,
    private val newsCompanyService: NewsCompanyService,
    private val runtimeStateRepository: RuntimeStateRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private enum class DispatchStatus(val logKey: String) {
        SENT("sent"),
        SKIPPED_DUPLICATE("duplicate"),
        SKIPPED_RULE("rule_excluded"),
        SKIPPED_SPAM("spam"),
        SKIPPED_MISSING_COMPANY("missing_company"),
        FAILED_PERSIST("persist_failed"),
        FAILED_LOOKUP("lookup_failed"),
        FAILED_SLACK("slack_failed"),
        FAILED_MISSING_ID("missing_id")
    }

    /**
     * 채널별 1회 실행 (스케줄러/수동 트리거에서 호출)
     */
    suspend fun runOnce(channel: NewsChannel) {
        val lastPollTimeKey = "last_poll_time_${channel.name}"
        val lastPollTimeString = runtimeStateRepository.selectState(lastPollTimeKey)

        // 1. 마지막 폴링 시간 파싱, 없으면 null
        val lastPollTime = lastPollTimeString?.let {
            try {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (e: Exception) {
                logger.error("Failed to parse stored lastPollTime: $it. Defaulting to null, will process all.", e)
                null
            }
        }

        val resp = naverNewsClient.search(channel.query, display = 30, start = 1, sort = "date")

        // 2. lastPollTime이 null이면 시간 필터링 없이 모든 아이템 처리
        val itemsToProcess = resp.items
            ?.filter { it.title?.contains(channel.query) ?: false }
            ?.filter { item ->
                if (lastPollTime == null) {
                    true // Process all if no last poll time
                } else {
                    val publishedAt = parsePubDate(item.pubDate)
                    publishedAt?.isAfter(lastPollTime) ?: false
                }
            }

        val eligibleItems = itemsToProcess.orEmpty()

        val summaryWhenEmpty = eligibleItems.isEmpty()

        val dispatchResults = if (summaryWhenEmpty) {
            emptyList()
        } else {
            coroutineScope {
                eligibleItems.map { item ->
                    val refinedTitle = refiner.refineTitle(item.title)
                    val tokens = spam.tokenizeTitle(refinedTitle)
                    async { processItem(channel, item, refinedTitle, tokens) }
                }
                    .awaitAll()
            }
        }
        val resultCounts = dispatchResults.groupingBy { it.status }.eachCount()
        val sentCount = resultCounts[DispatchStatus.SENT] ?: 0
        val duplicateCount = resultCounts[DispatchStatus.SKIPPED_DUPLICATE] ?: 0
        val spamCount = resultCounts[DispatchStatus.SKIPPED_SPAM] ?: 0

        // 3. 처리된 마지막 아이템의 시간으로 lastPollTime 업데이트
        if (!summaryWhenEmpty) {
            val lastItemTime = parsePubDate(eligibleItems.last().pubDate)
            if (lastItemTime != null) {
                runtimeStateRepository.updateState(lastPollTimeKey, lastItemTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            } else {
                logger.warn(append("channel", channel.name), "Could not parse pubDate of the last item. Last poll time not updated.")
            }
        }

        val summaryMarker = appendEntries(
            mapOf(
                "channel" to channel.name,
                "eligible" to eligibleItems.size,
                "sent" to sentCount,
                "duplicateSkips" to duplicateCount,
                "spamSkips" to spamCount,
            )
        )
        dispatchResults.forEach { result ->
            logKeywordEvent(channel, result.status, result.title, result.tokens)
        }
        logger.info(
            summaryMarker,
            "channel={} eligible={} sent={} duplicateSkips={} spamSkips={}",
            channel.name,
            eligibleItems.size,
            sentCount,
            duplicateCount,
            spamCount
        )
    }

    // Helper function to parse date to avoid repetition
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

    fun getPayloadAsMap(news: News): Map<String, Any?> {
        return mapOf(
            "text" to news.title, // 슬랙 알림을 위한 필수 필드
            "blocks" to listOf(
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "mrkdwn",
                        "text" to "*<${news.link}|${news.title}>*"
                    )
                ),
                // mapOf(
                //     "type" to "context",
                //     "elements" to listOf(
                //         mapOf(
                //             "type" to "plain_text",
                //             "text" to news.description
                //         )
                //     )
                // ),
                mapOf(
                    "type" to "context",
                    "elements" to listOf(
                        mapOf(
                            "type" to "plain_text",
                            "text" to "${toKoreanDateTimeStringOneLiner(news.pubDate!!)} | ${news.company}"
                        )
                    )
                ),
                mapOf(
                    "type" to "divider"
                )
            )
        )
    }

    fun toKoreanDateTimeStringOneLiner(dateString: String): String =
        ZonedDateTime.parse(dateString, DateTimeFormatter.RFC_1123_DATE_TIME)
            .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (EEEE) h:mm:ss a", Locale.KOREAN))

    private data class DispatchResult(
        val status: DispatchStatus,
        val title: String,
        val tokens: List<String>
    )

    private suspend fun processItem(channel: NewsChannel, item: Item, refinedTitle: String, titleTokens: List<String>): DispatchResult {
        val contextMap = buildMap {
            put("channel", channel.name)
            item.link?.let { put("naver_link", it) }
        }

        return withContext(Dispatchers.IO + MDCContext(contextMap)) {
            val title = refinedTitle
            val description = refiner.refineDescription(item.description)
            val companyDomain = refiner.extractCompany(item.link) ?: run {
                logger.warn(append("reason", "missing_company"), "Unable to extract company from link")
                return@withContext DispatchResult(DispatchStatus.SKIPPED_MISSING_COMPANY, title, titleTokens)
            }
            val company = newsCompanyService.findOrCreateCompany(companyDomain)

            val normalizedUrl = HashUtils.normalizeUrl(item.link)
            val hash = HashUtils.sha256(normalizedUrl + company.id)

            MDC.put("article_hash", hash)
            try {
                // 중복 기사 방지
                if (articleRepo.countNewsArticleByHash(hash) > 0L) {
                    logger.debug(append("reason", "duplicate"), "Article already processed. Skipping.")
                    return@withContext DispatchResult(DispatchStatus.SKIPPED_DUPLICATE, title, titleTokens)
                }

                // 제외 룰 검사
                if (filter.isExcluded(title, company.name, channel)) {
                    logger.debug(append("reason", "exclusion_rule"), "Article excluded by rule.")
                    return@withContext DispatchResult(DispatchStatus.SKIPPED_RULE, title, titleTokens)
                }

                // 스팸(중복 키워드) 검사
                if (spam.isSpamByTitleTokens(title)) {
                    logger.debug(append("reason", "spam_detected"), "Article classified as spam.")
                    return@withContext DispatchResult(DispatchStatus.SKIPPED_SPAM, title, titleTokens)
                }

                // 기사 저장
                val savedRows = articleRepo.insertNewsArticle(
                    naverLinkHash = hash,
                    link = item.link!!,
                    originalLink = item.link,
                    title = title,
                    summary = description,
                    companyId = company.id,
                    publishedAt = LocalDateTime.now(), // 필요 시 refiner.pubDateToKst(item.pubDate) 파싱하여 사용
                    fetchedAt = LocalDateTime.now(),
                    rawJson = null
                )
                if (savedRows <= 0) {
                    logger.warn(append("reason", "persist_failed"), "Failed to insert news article.")
                    return@withContext DispatchResult(DispatchStatus.FAILED_PERSIST, title, titleTokens)
                }

                val news = News(
                    title = title,
                    originalLink = item.link,
                    link = item.link,
                    company = company.name,
                    description = description,
                    pubDate = item.pubDate
                )
                val payload = getPayloadAsMap(news)

                // id 조회(RETURNING 미사용이므로 재조회)
                val saved = articleRepo.selectNewsArticleByHash(hash)
                if (saved == null) {
                    logger.warn(append("reason", "lookup_failed"), "Inserted article could not be reloaded.")
                    return@withContext DispatchResult(DispatchStatus.FAILED_LOOKUP, title, titleTokens)
                }

                val sendResult = slack.send(channel, payload)

                val articleId = saved.id ?: run {
                    logger.warn(append("reason", "missing_id"), "Reloaded article entity does not have an id.")
                    return@withContext DispatchResult(DispatchStatus.FAILED_MISSING_ID, title, titleTokens)
                }

                // 전송 로그
                deliveryRepo.insertDeliveryLog(
                    articleId = articleId,
                    channel = channel.name,
                    status = if (sendResult.success) "SUCCESS" else "FAILED",
                    httpStatus = sendResult.httpStatus,
                    sentAt = LocalDateTime.now(),
                    responseBody = sendResult.body
                )

                val deliveryBase = mapOf(
                    "articleId" to articleId,
                    "slackStatus" to sendResult.success
                )
                if (sendResult.success) {
                    logger.debug(appendEntries(deliveryBase), "Article delivered to Slack.")
                } else {
                    logger.warn(
                        appendEntries(deliveryBase + ("httpStatus" to (sendResult.httpStatus ?: -1))),
                        "Slack delivery failed."
                    )
                }

                if (sendResult.success) {
                    DispatchResult(DispatchStatus.SENT, title, titleTokens)
                } else {
                    DispatchResult(DispatchStatus.FAILED_SLACK, title, titleTokens)
                }
            } finally {
                MDC.remove("article_hash")
            }
        }
    }

    private fun logKeywordEvent(channel: NewsChannel, status: DispatchStatus, title: String, tokens: List<String>) {
        val shouldLog = when (status) {
            DispatchStatus.SENT, DispatchStatus.SKIPPED_DUPLICATE, DispatchStatus.SKIPPED_SPAM -> true
            else -> false
        }
        if (!shouldLog) return

        val marker = appendEntries(
            mapOf(
                "title" to title,
                "keywords" to tokens,
            )
        )
        logger.info(marker, "title={} keywords={}", title, tokens.joinToString(","))
    }
}
