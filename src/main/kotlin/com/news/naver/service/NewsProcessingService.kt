package com.news.naver.service

import com.news.naver.client.SlackClient
import com.news.naver.data.dto.Item
import com.news.naver.data.dto.News
import com.news.naver.data.enums.NewsChannel
import com.news.naver.entity.NewsCompanyEntity
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import com.news.naver.repository.RuntimeStateRepository
import com.news.naver.util.DateTimeUtils
import com.news.naver.util.hash.HashUtils
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
import java.time.format.DateTimeFormatter
import net.logstash.logback.marker.Markers.append
import net.logstash.logback.marker.Markers.appendEntries

@Service
class NewsProcessingService(
    private val slack: SlackClient,
    private val refiner: com.news.naver.util.refiner.NewsRefinerService,
    private val filter: NewsFilterService,
    private val spam: NewsSpamFilterService,
    private val articleRepo: NewsArticleRepository,
    private val deliveryRepo: DeliveryLogRepository,
    private val newsCompanyService: NewsCompanyService,
    private val runtimeStateRepository: RuntimeStateRepository,
    private val itemProcessor: com.news.naver.util.processor.NewsItemProcessor,
    private val slackFormatter: com.news.naver.util.formatter.SlackMessageFormatter
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private enum class DispatchStatus(val logKey: String) {
        SENT("sent"),
        SKIPPED_DUPLICATE("duplicate"),
        SKIPPED_RULE("rule_excluded"),
        SKIPPED_SPAM("spam"),
        SKIPPED_MISSING_COMPANY("missing_company"),
        SKIPPED_CHATGPT("chatgpt_filtered"),
        FAILED_PERSIST("persist_failed"),
        FAILED_LOOKUP("lookup_failed"),
        FAILED_SLACK("slack_failed"),
        FAILED_MISSING_ID("missing_id")
    }

    /**
     * 채널별 1회 실행 (스케줄러/수동 트리거에서 호출)
     */
    suspend fun runOnce(channel: NewsChannel) {
        val items = itemProcessor.fetchItems(channel.query)
        val lastPollTime = loadLastPollTime(channel)
        val timeFilteredItems = itemProcessor.filterByTime(items, lastPollTime)
        val chatGPTFilteredItems = itemProcessor.filterByChatGPT(timeFilteredItems)
        val dispatchResults = processNewsItems(channel, chatGPTFilteredItems)

        updateLastPollTime(channel, chatGPTFilteredItems)
        logProcessingResults(channel, chatGPTFilteredItems, dispatchResults)
    }

    /**
     * 마지막 폴링 시간을 로드합니다.
     */
    private suspend fun loadLastPollTime(channel: NewsChannel): LocalDateTime? {
        val lastPollTimeKey = "last_poll_time_${channel.name}"
        val lastPollTimeString = runtimeStateRepository.selectState(lastPollTimeKey)

        return lastPollTimeString?.let {
            try {
                LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (e: Exception) {
                logger.error("Failed to parse stored lastPollTime: $it. Defaulting to null, will process all.", e)
                null
            }
        }
    }


    /**
     * 뉴스 아이템들을 처리합니다.
     */
    private suspend fun processNewsItems(
        channel: NewsChannel,
        items: List<Item>
    ): List<DispatchResult> {
        if (items.isEmpty()) {
            return emptyList()
        }

        return coroutineScope {
            items.map { item ->
                val refinedTitle = refiner.refineTitle(item.title)
                val tokens = spam.tokenizeTitle(refinedTitle)
                async { processItem(channel, item, refinedTitle, tokens) }
            }.awaitAll()
        }
    }

    /**
     * 마지막 폴링 시간을 업데이트합니다.
     */
    private suspend fun updateLastPollTime(channel: NewsChannel, items: List<Item>) {
        if (items.isEmpty()) {
            return
        }

        val lastPollTimeKey = "last_poll_time_${channel.name}"
        val lastItemTime = itemProcessor.parsePubDate(items.last().pubDate)

        if (lastItemTime != null) {
            runtimeStateRepository.updateState(
                lastPollTimeKey,
                lastItemTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        } else {
            logger.warn(
                append("channel", channel.name),
                "Could not parse pubDate of the last item. Last poll time not updated."
            )
        }
    }

    /**
     * 처리 결과를 로깅합니다.
     */
    private fun logProcessingResults(
        channel: NewsChannel,
        items: List<Item>,
        dispatchResults: List<DispatchResult>
    ) {
        val resultCounts = dispatchResults.groupingBy { it.status }.eachCount()
        val sentCount = resultCounts[DispatchStatus.SENT] ?: 0
        val duplicateCount = resultCounts[DispatchStatus.SKIPPED_DUPLICATE] ?: 0
        val spamCount = resultCounts[DispatchStatus.SKIPPED_SPAM] ?: 0
        val chatGPTFilteredCount = resultCounts[DispatchStatus.SKIPPED_CHATGPT] ?: 0

        val summaryMarker = appendEntries(
            mapOf(
                "channel" to channel.name,
                "eligible" to items.size,
                "sent" to sentCount,
                "duplicateSkips" to duplicateCount,
                "spamSkips" to spamCount,
                "chatgptSkips" to chatGPTFilteredCount
            )
        )

        dispatchResults.forEach { result ->
            logKeywordEvent(channel, result.status, result.title, result.tokens)
        }

        logger.info(
            summaryMarker,
            "channel={} eligible={} sent={} duplicateSkips={} spamSkips={} chatgptSkips={}",
            channel.name,
            items.size,
            sentCount,
            duplicateCount,
            spamCount,
            chatGPTFilteredCount
        )
    }


    private data class DispatchResult(
        val status: DispatchStatus,
        val title: String,
        val tokens: List<String>
    )

    private suspend fun processItem(
        channel: NewsChannel,
        item: Item,
        refinedTitle: String,
        titleTokens: List<String>
    ): DispatchResult {
        val contextMap = buildMap {
            put("channel", channel.name)
            item.link?.let { put("naver_link", it) }
        }

        return withContext(Dispatchers.IO + MDCContext(contextMap)) {
            val description = refiner.refineDescription(item.description)

            // 1. 회사 정보 추출
            val companyResult = extractCompanyInfo(item, refinedTitle, titleTokens)
            if (companyResult is CompanyExtractionResult.Failed) {
                return@withContext companyResult.dispatchResult
            }
            val company = (companyResult as CompanyExtractionResult.Success).company

            // 2. 해시 생성 및 검증
            val validationResult = validateArticle(item, refinedTitle, titleTokens, company, channel)
            if (validationResult is ValidationResult.Failed) {
                return@withContext validationResult.dispatchResult
            }
            val hash = (validationResult as ValidationResult.Success).hash

            MDC.put("article_hash", hash)
            try {
                // 3. 기사 저장
                val saveResult = saveArticle(item, refinedTitle, description, company, hash)
                if (saveResult is SaveResult.Failed) {
                    return@withContext saveResult.dispatchResult
                }
                val articleId = (saveResult as SaveResult.Success).articleId

                // 4. 슬랙 전송 및 로깅
                val deliveryResult = deliverToSlack(channel, item, refinedTitle, description, company, articleId)
                deliveryResult
            } finally {
                MDC.remove("article_hash")
            }
        }
    }

    /**
     * 회사 정보를 추출합니다.
     */
    private suspend fun extractCompanyInfo(
        item: Item,
        title: String,
        titleTokens: List<String>
    ): CompanyExtractionResult {
        val companyDomain = refiner.extractCompany(item.link) ?: run {
            logger.warn(append("reason", "missing_company"), "Unable to extract company from link")
            return CompanyExtractionResult.Failed(
                DispatchResult(DispatchStatus.SKIPPED_MISSING_COMPANY, title, titleTokens)
            )
        }
        val company = newsCompanyService.findOrCreateCompany(companyDomain)
        return CompanyExtractionResult.Success(company)
    }

    /**
     * 기사의 유효성을 검증합니다 (중복, 제외 룰, 스팸).
     */
    private suspend fun validateArticle(
        item: Item,
        title: String,
        titleTokens: List<String>,
        company: NewsCompanyEntity,
        channel: NewsChannel
    ): ValidationResult {
        val normalizedUrl = HashUtils.normalizeUrl(item.link)
        val hash = HashUtils.sha256(normalizedUrl + company.id)

        // 중복 기사 방지
        if (articleRepo.countNewsArticleByHash(hash) > 0L) {
            logger.debug(append("reason", "duplicate"), "Article already processed. Skipping.")
            return ValidationResult.Failed(
                DispatchResult(DispatchStatus.SKIPPED_DUPLICATE, title, titleTokens)
            )
        }

        // 제외 룰 검사
        if (filter.isExcluded(title, company.name, channel)) {
            logger.debug(append("reason", "exclusion_rule"), "Article excluded by rule.")
            return ValidationResult.Failed(
                DispatchResult(DispatchStatus.SKIPPED_RULE, title, titleTokens)
            )
        }

        // 스팸(중복 키워드) 검사
        if (spam.isSpamByTitleTokens(title)) {
            logger.debug(append("reason", "spam_detected"), "Article classified as spam.")
            return ValidationResult.Failed(
                DispatchResult(DispatchStatus.SKIPPED_SPAM, title, titleTokens)
            )
        }

        return ValidationResult.Success(hash)
    }

    /**
     * 기사를 데이터베이스에 저장합니다.
     */
    private suspend fun saveArticle(
        item: Item,
        title: String,
        description: String,
        company: NewsCompanyEntity,
        hash: String
    ): SaveResult {
        val savedRows = articleRepo.insertNewsArticle(
            naverLinkHash = hash,
            link = item.link!!,
            originalLink = item.link,
            title = title,
            summary = description,
            companyId = company.id,
            publishedAt = DateTimeUtils.now(),
            fetchedAt = DateTimeUtils.now(),
            rawJson = null
        )

        if (savedRows <= 0) {
            logger.warn(append("reason", "persist_failed"), "Failed to insert news article.")
            return SaveResult.Failed(
                DispatchResult(DispatchStatus.FAILED_PERSIST, title, emptyList())
            )
        }

        val saved = articleRepo.selectNewsArticleByHash(hash)
        if (saved == null) {
            logger.warn(append("reason", "lookup_failed"), "Inserted article could not be reloaded.")
            return SaveResult.Failed(
                DispatchResult(DispatchStatus.FAILED_LOOKUP, title, emptyList())
            )
        }

        val articleId = saved.id ?: run {
            logger.warn(append("reason", "missing_id"), "Reloaded article entity does not have an id.")
            return SaveResult.Failed(
                DispatchResult(DispatchStatus.FAILED_MISSING_ID, title, emptyList())
            )
        }

        return SaveResult.Success(articleId)
    }

    /**
     * 슬랙으로 기사를 전송하고 로깅합니다.
     */
    private suspend fun deliverToSlack(
        channel: NewsChannel,
        item: Item,
        title: String,
        description: String,
        company: NewsCompanyEntity,
        articleId: Long
    ): DispatchResult {
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

        deliveryRepo.insertDeliveryLog(
            articleId = articleId,
            channel = channel.name,
            status = if (sendResult.success) "SUCCESS" else "FAILED",
            httpStatus = sendResult.httpStatus,
            sentAt = DateTimeUtils.now(),
            responseBody = sendResult.body
        )

        val deliveryBase = mapOf(
            "articleId" to articleId,
            "slackStatus" to sendResult.success
        )

        if (sendResult.success) {
            logger.debug(appendEntries(deliveryBase), "Article delivered to Slack.")
            return DispatchResult(DispatchStatus.SENT, title, emptyList())
        } else {
            logger.warn(
                appendEntries(deliveryBase + ("httpStatus" to (sendResult.httpStatus ?: -1))),
                "Slack delivery failed."
            )
            return DispatchResult(DispatchStatus.FAILED_SLACK, title, emptyList())
        }
    }

    private fun logKeywordEvent(
        channel: NewsChannel,
        status: DispatchStatus,
        title: String,
        tokens: List<String>
    ) {
        val shouldLog = when (status) {
            DispatchStatus.SENT, DispatchStatus.SKIPPED_DUPLICATE, DispatchStatus.SKIPPED_SPAM -> true
            else -> false
        }
        if (!shouldLog) return

        val marker = appendEntries(
            mapOf(
                "channel" to channel.name,
                "title" to title,
                "keywords" to tokens,
            )
        )
        logger.info(marker, "channel={} title={} keywords={}", channel.name, title, tokens.joinToString(","))
    }

    // Helper sealed classes for type-safe results
    private sealed class CompanyExtractionResult {
        data class Success(val company: NewsCompanyEntity) : CompanyExtractionResult()
        data class Failed(val dispatchResult: DispatchResult) : CompanyExtractionResult()
    }

    private sealed class ValidationResult {
        data class Success(val hash: String) : ValidationResult()
        data class Failed(val dispatchResult: DispatchResult) : ValidationResult()
    }

    private sealed class SaveResult {
        data class Success(val articleId: Long) : SaveResult()
        data class Failed(val dispatchResult: DispatchResult) : SaveResult()
    }
}
