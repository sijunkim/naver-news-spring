package com.news.naver.service

import com.news.naver.client.NaverNewsClient
import com.news.naver.client.SlackClient
import com.news.naver.common.HashUtils
import com.news.naver.data.constant.MessageConstants
import com.news.naver.data.dto.Item
import com.news.naver.data.enum.NewsChannel
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import com.news.naver.repository.RuntimeStateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
            logger.info("Channel [${channel.name}]: No valid last poll time. Processing all fetched items.")
        } else {
            logger.info("Channel [${channel.name}]: Last poll time for filtering: $lastPollTime")
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
            logger.info("No new items found for channel: ${channel.name} after filtering by timestamp.")
            // 처리할 아이템이 없으면 lastPollTime을 업데이트하지 않음
            return
        }

        val sentCount = coroutineScope {
            itemsToProcess.map { async { processItem(channel, it) } }
                .awaitAll()
                .count { it } // Count successful sends
        }
        logger.info("[${channel.name}] $sentCount news items sent to Slack.")

        // 3. 처리된 마지막 아이템의 시간으로 lastPollTime 업데이트
        val lastItemTime = parsePubDate(itemsToProcess.last().pubDate)
        if (lastItemTime != null) {
            runtimeStateRepository.updateState(lastPollTimeKey, lastItemTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            logger.info("Channel [${channel.name}]: Updated last poll time to $lastItemTime")
        } else {
            logger.warn("Channel [${channel.name}]: Could not parse pubDate of the last item. Last poll time not updated.")
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

    private suspend fun processItem(channel: NewsChannel, item: Item): Boolean { // Changed return type to Boolean
        val title = refiner.refineTitle(item.title)
        val description = refiner.refineDescription(item.description)
        val companyDomain = refiner.extractCompany(item.link)
        val company = companyDomain?.let { newsCompanyService.findOrCreateCompany(it) }

        val normalizedUrl = HashUtils.normalizeUrl(item.link)
        val hash = HashUtils.sha256(normalizedUrl)

        // 중복 기사 방지
        if (articleRepo.countNewsArticleByHash(hash) > 0L) return false // Return false if duplicate

        // 제외 룰 검사
        if (filter.isExcluded(title, company?.name, channel)) return false // Return false if excluded

        // 스팸(중복 키워드) 검사
        val isSpam = spam.isSpamByTitleTokens(title)
        if (isSpam) return false // Return false if spam

        // 기사 저장
        val savedRows = articleRepo.insertNewsArticle(
            naverLinkHash = hash,
            link = item.link!!,
            title = title,
            summary = description,
            companyId = company?.id,
            publishedAt = LocalDateTime.now(), // 필요 시 refiner.pubDateToKst(item.pubDate) 파싱하여 사용
            fetchedAt = LocalDateTime.now(),
            rawJson = null
        )
        if (savedRows <= 0) return false // Return false if not saved

        // id 조회(RETURNING 미사용이므로 재조회)
        val saved = articleRepo.selectNewsArticleByHash(hash) ?: return false // Return false if not found after save

        // Slack 전송
        val prefix = when (channel) {
            NewsChannel.BREAKING -> MessageConstants.SLACK_PREFIX_BREAKING
            NewsChannel.EXCLUSIVE -> MessageConstants.SLACK_PREFIX_EXCLUSIVE
            NewsChannel.DEV -> MessageConstants.SLACK_PREFIX_DEV
        }
        val text = refiner.slackText(prefix, title, normalizedUrl, company?.name)
        val sendResult = slack.send(channel, text)

        // 전송 로그
        deliveryRepo.insertDeliveryLog(
            articleId = saved.id!!,
            channel = channel.name,
            status = if (sendResult.success) "SUCCESS" else "FAILED",
            httpStatus = sendResult.httpStatus,
            sentAt = LocalDateTime.now(),
            responseBody = sendResult.body
        )

        // 스팸 키워드 기록(윈도우 집계를 위한 이벤트)
        spam.recordTitleTokens(title)

        return sendResult.success // Return true if sent successfully
    }
}
