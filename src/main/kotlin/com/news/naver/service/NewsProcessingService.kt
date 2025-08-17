"""package com.news.naver.service

import com.news.naver.client.NaverNewsClient
import com.news.naver.client.SlackClient
import com.news.naver.common.HashUtils
import com.news.naver.data.constant.MessageConstants
import com.news.naver.data.dto.Item
import com.news.naver.data.enum.NewsChannel
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import com.news.naver.repository.NewsCompanyRepository
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
    private val newsCompanyRepository: NewsCompanyRepository,
    private val runtimeStateRepo: RuntimeStateRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 채널별 1회 실행 (스케줄러/수동 트리거에서 호출)
     */
    suspend fun runOnce(channel: NewsChannel) {
        val lastPollTimeStr = runtimeStateRepo.getState("lastPollTime:${channel.name}")
        val lastPollTime = lastPollTimeStr?.let { LocalDateTime.parse(it) }

        val resp = naverNewsClient.search(channel.query, display = 30, start = 1, sort = "date")

        // 제목에 키워드 포함된 기사만 선별
        val fetchedItems = resp.items?.filter { it.title?.contains(channel.query) ?: false }

        if (fetchedItems.isNullOrEmpty()) {
            logger.info("No items found for channel: ${channel.name}")
            return
        }

        // 마지막 조회 시간 이후 발행된 뉴스만 필터링
        val newItems = if (lastPollTime == null) {
            fetchedItems
        } else {
            fetchedItems.filter { refiner.pubDateToKst(it.pubDate).isAfter(lastPollTime) }
        }

        if (newItems.isEmpty()) {
            logger.info("No new items found for channel: ${channel.name} since $lastPollTime")
            return
        }

        val sentCount = coroutineScope {
            newItems.map { async { processItem(channel, it) } }
                .awaitAll()
                .count { it } // Count successful sends
        }
        logger.info("[${channel.name}] $sentCount news items sent to Slack.")

        // 마지막 조회 시간 업데이트
        val latestPubDate = newItems.maxOfOrNull { refiner.pubDateToKst(it.pubDate) }
        if (latestPubDate != null) {
            runtimeStateRepo.setState("lastPollTime:${channel.name}", latestPubDate.toString())
        }
    }

    private suspend fun processItem(channel: NewsChannel, item: Item): Boolean { // Changed return type to Boolean
        val title = refiner.refineTitle(item.title)
        val description = refiner.refineDescription(item.description)
        val companyDomain = refiner.extractCompany(item.link, item.originalLink)
        val company = companyDomain?.let { newsCompanyRepository.selectNewsCompanyByDomainPrefix(it) }

        val normalizedUrl = HashUtils.normalizeUrl(item.link)
        val hash = HashUtils.sha256(normalizedUrl)

        // 중복 기사 방지
        if (articleRepo.countNewsArticleByHash(hash) > 0L) return false // Return false if duplicate

        // 제외 룰 검사
        if (filter.isExcluded(title, company?.name, channel)) return false // Return false if excluded

        // 스팸(중복 키워드) 검사
        val isSpam = spam.isSpamByTitleTokens(title, threshold = 5 /* app.duplicate.threshold 사용 가능 */)
        if (isSpam) return false // Return false if spam

        // 기사 저장
        val savedRows = articleRepo.insertNewsArticle(
            naverLinkHash = hash,
            title = title,
            summary = description,
            companyId = company?.id,
            publishedAt = refiner.pubDateToKst(item.pubDate),
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
""