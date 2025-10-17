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

        if (lastPollTime == null) {
            logger.info(append("channel", channel.name), "No valid last poll time. Processing all fetched items.")
        } else {
            logger.info(
                append("channel", channel.name).and(append("lastPollTime", lastPollTime.toString())),
                "Loaded last poll time for filtering"
            )
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

        if (itemsToProcess.isNullOrEmpty()) {
            logger.info(
                append("channel", channel.name),
                "No new items found after filtering by timestamp"
            )
            // 처리할 아이템이 없으면 lastPollTime을 업데이트하지 않음
            return
        }

        val sentCount = coroutineScope {
            itemsToProcess.map { async { processItem(channel, it) } }
                .awaitAll()
                .count { it } // Count successful sends
        }
        val dispatchMarker = appendEntries(
            mapOf(
                "channel" to channel.name,
                "sentCount" to sentCount,
                "fetchedCount" to itemsToProcess.size
            )
        )
        logger.info(dispatchMarker, "Completed dispatch of news items")

        // 3. 처리된 마지막 아이템의 시간으로 lastPollTime 업데이트
        val lastItemTime = parsePubDate(itemsToProcess.last().pubDate)
        if (lastItemTime != null) {
            runtimeStateRepository.updateState(lastPollTimeKey, lastItemTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            val pollMarker = appendEntries(
                mapOf(
                    "channel" to channel.name,
                    "lastPollTime" to lastItemTime.toString()
                )
            )
            logger.info(pollMarker, "Updated last poll time")
        } else {
            logger.warn(append("channel", channel.name), "Could not parse pubDate of the last item. Last poll time not updated.")
        }
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

    private suspend fun processItem(channel: NewsChannel, item: Item): Boolean {
        val contextMap = buildMap {
            put("channel", channel.name)
            item.link?.let { put("naver_link", it) }
        }

        return withContext(Dispatchers.IO + MDCContext(contextMap)) {
            val title = refiner.refineTitle(item.title)
            val description = refiner.refineDescription(item.description)
            val companyDomain = refiner.extractCompany(item.link) ?: run {
                logger.warn(append("reason", "missing_company"), "Unable to extract company from link")
                return@withContext false
            }
            val company = newsCompanyService.findOrCreateCompany(companyDomain)

            val normalizedUrl = HashUtils.normalizeUrl(item.link)
            val hash = HashUtils.sha256(normalizedUrl + company.id)

            MDC.put("article_hash", hash)
            try {
                // 중복 기사 방지
                if (articleRepo.countNewsArticleByHash(hash) > 0L) {
                    logger.debug(append("reason", "duplicate"), "Article already processed. Skipping.")
                    return@withContext false
                }

                // 제외 룰 검사
                if (filter.isExcluded(title, company.name, channel)) {
                    logger.debug(append("reason", "exclusion_rule"), "Article excluded by rule.")
                    return@withContext false
                }

                // 스팸(중복 키워드) 검사
                if (spam.isSpamByTitleTokens(title)) {
                    logger.debug(append("reason", "spam_detected"), "Article classified as spam.")
                    return@withContext false
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
                    return@withContext false
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
                    return@withContext false
                }

                val sendResult = slack.send(channel, payload)

                val articleId = saved.id ?: run {
                    logger.warn(append("reason", "missing_id"), "Reloaded article entity does not have an id.")
                    return@withContext false
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

                // 스팸 키워드 기록(윈도우 집계를 위한 이벤트)
                spam.recordTitleTokens(title)

                val deliveryBase = mapOf(
                    "articleId" to articleId,
                    "slackStatus" to sendResult.success
                )
                if (sendResult.success) {
                    logger.info(appendEntries(deliveryBase), "Article delivered to Slack.")
                } else {
                    logger.warn(
                        appendEntries(deliveryBase + ("httpStatus" to (sendResult.httpStatus ?: -1))),
                        "Slack delivery failed."
                    )
                }

                sendResult.success
            } finally {
                MDC.remove("article_hash")
            }
        }
    }
}
