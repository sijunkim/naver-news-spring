package com.news.naver.service

import com.news.naver.client.NaverNewsClient
import com.news.naver.client.SlackClient
import com.news.naver.common.HashUtils
import com.news.naver.data.constant.MessageConstants
import com.news.naver.data.dto.Item
import com.news.naver.data.enum.NewsChannel
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import com.news.naver.repository.NewsCompanyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NewsProcessingService(
    private val naverNewsClient: NaverNewsClient,
    private val slack: SlackClient,
    private val refiner: NewsRefinerService,
    private val filter: NewsFilterService,
    private val spam: NewsSpamFilterService,
    private val articleRepo: NewsArticleRepository,
    private val deliveryRepo: DeliveryLogRepository,
    private val newsCompanyRepository: NewsCompanyRepository
) {

    /**
     * 채널별 1회 실행 (스케줄러/수동 트리거에서 호출)
     */
    suspend fun runOnce(channel: NewsChannel) {
        val resp = naverNewsClient.search(channel.query, display = 30, start = 1, sort = "date")

        // NestJS 동작과 동일하게: 제목에 키워드 포함된 기사만 선별
        val items = resp.items?.filter { it.title?.contains(channel.query) ?: false }

        coroutineScope {
            items?.map { async { processItem(channel, it) } }?.awaitAll() ?: run {
                println("No items found for channel: ${channel.name}")
            }
        }
    }

    private suspend fun processItem(channel: NewsChannel, item: Item) {
        val title = refiner.refineTitle(item.title)
        val description = refiner.refineDescription(item.description)
        val companyDomain = refiner.extractCompany(item.link, item.originalLink)
        val company = companyDomain?.let { newsCompanyRepository.selectNewsCompanyByDomainPrefix(it) }

        val normalizedUrl = HashUtils.normalizeUrl(item.link)
        val hash = HashUtils.sha256(normalizedUrl)

        // 중복 기사 방지
        if (articleRepo.countNewsArticleByHash(hash) > 0L) return

        // 제외 룰 검사
        if (filter.isExcluded(title, company?.name, channel)) return

        // 스팸(중복 키워드) 검사
        val isSpam = spam.isSpamByTitleTokens(title, threshold = 5 /* app.duplicate.threshold 사용 가능 */)
        if (isSpam) return

        // 기사 저장
        val savedRows = articleRepo.insertNewsArticle(
            naverLinkHash = hash,
            title = title,
            summary = description,
            companyId = company?.id,
            publishedAt = LocalDateTime.now(), // 필요 시 refiner.pubDateToKst(item.pubDate) 파싱하여 사용
            fetchedAt = LocalDateTime.now(),
            rawJson = null
        )
        if (savedRows <= 0) return

        // id 조회(RETURNING 미사용이므로 재조회)
        val saved = articleRepo.selectNewsArticleByHash(hash) ?: return

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
    }
}