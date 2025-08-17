"""package com.news.naver.service

import com.news.naver.client.NaverNewsClient
import com.news.naver.client.SlackClient
import com.news.naver.common.HashUtils
import com.news.naver.data.constant.MessageConstants
import com.news.naver.data.constant.StringConstants
import com.news.naver.data.dto.Item
import com.news.naver.data.enum.NewsChannel
import com.news.naver.entity.NewsArticleEntity
import com.news.naver.property.NaverProperties
import com.news.naver.repository.NewsCompanyRepository
import com.news.naver.repository.RuntimeStateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NewsProcessingService(
    private val naverNewsClient: NaverNewsClient,
    private val slack: SlackClient,
    private val refiner: NewsRefinerService,
    private val filter: NewsFilterService,
    private val spam: NewsSpamFilterService,
    private val persistence: ArticlePersistenceService,
    private val newsCompanyRepository: NewsCompanyRepository,
    private val runtimeStateRepo: RuntimeStateRepository,
    private val naverProperties: NaverProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 채널별 1회 실행 (스케줄러/수동 트리거에서 호출)
     */
    suspend fun runOnce(channel: NewsChannel) {
        // 1. 마지막 조회 시간 가져오기
        val lastPollTimeKey = "${StringConstants.LAST_POLL_TIME_PREFIX}${channel.name}"
        val lastPollTime = runtimeStateRepo.selectState(lastPollTimeKey)?.let { LocalDateTime.parse(it) }

        // 2. 네이버 API를 통해 뉴스 아이템 가져오기
        val fetchedItems = fetchItemsFromNaver(channel)
        if (fetchedItems.isEmpty()) {
            logger.info("No items found for channel: ${channel.name}")
            return
        }

        // 3. 마지막 조회 시간 이후의 새 기사 필터링
        val newItems = filterItemsByTime(fetchedItems, lastPollTime)
        if (newItems.isEmpty()) {
            logger.info("No new items found for channel: ${channel.name} since $lastPollTime")
            return
        }

        // 4. 저장할 기사 후보 목록 생성 (메모리에서 사전 필터링)
        val candidateArticles = prefilterCandidates(newItems, channel)

        // 5. 중복 기사 일괄 확인 및 최종 저장 목록 필터링
        val trulyNewArticles = filterOutExistingArticles(candidateArticles)
        if (trulyNewArticles.isEmpty()) {
            logger.info("No truly new articles to save for channel: ${channel.name}")
            updateLastPollTime(lastPollTimeKey, newItems) // 중복만 있었어도 시간은 업데이트
            return
        }

        // 6. 신규 기사 일괄 저장
        persistence.insertBulkArticles(trulyNewArticles)

        // 7. 저장된 기사 정보 다시 조회 (ID 확보 목적)
        val savedArticles = persistence.selectArticlesByHashes(trulyNewArticles.map { it.naverLinkHash })

        // 8. 후속 처리 (슬랙 전송 등)
        val sentCount = postProcessSavedArticles(savedArticles, channel)
        logger.info("[${channel.name}] $sentCount news items sent to Slack.")

        // 9. 마지막 조회 시간 업데이트
        updateLastPollTime(lastPollTimeKey, newItems)
    }

    private suspend fun fetchItemsFromNaver(channel: NewsChannel): List<Item> {
        val resp = naverNewsClient.search(
            query = channel.query,
            display = naverProperties.search.display,
            start = 1,
            sort = "date"
        )
        return resp.items?.filter { it.title?.contains(channel.query) ?: false } ?: emptyList()
    }

    private fun filterItemsByTime(items: List<Item>, lastPollTime: LocalDateTime?): List<Item> {
        if (lastPollTime == null) return items
        return items.filter { refiner.pubDateToKst(it.pubDate).isAfter(lastPollTime) }
    }

    private suspend fun prefilterCandidates(items: List<Item>, channel: NewsChannel): List<NewsArticleEntity> {
        return coroutineScope {
            items.map {
                async {
                    val title = refiner.refineTitle(it.title)
                    val companyDomain = refiner.extractCompany(it.link, it.originalLink)
                    val company = companyDomain?.let { newsCompanyRepository.selectNewsCompanyByDomainPrefix(it) }

                    if (!filter.shouldProcess(title, company?.name, channel)) {
                        null
                    } else {
                        NewsArticleEntity(
                            naverLinkHash = HashUtils.sha256(HashUtils.normalizeUrl(it.link)),
                            title = title,
                            summary = refiner.refineDescription(it.description),
                            companyId = company?.id,
                            publishedAt = refiner.pubDateToKst(it.pubDate),
                            fetchedAt = LocalDateTime.now(),
                            rawJson = null
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun filterOutExistingArticles(articles: List<NewsArticleEntity>): List<NewsArticleEntity> {
        if (articles.isEmpty()) return emptyList()
        val existingHashes = persistence.selectArticlesByHashes(articles.map { it.naverLinkHash }).map { it.naverLinkHash }.toSet()
        return articles.filter { it.naverLinkHash !in existingHashes }
    }

    private suspend fun postProcessSavedArticles(articles: List<NewsArticleEntity>, channel: NewsChannel): Int {
        return coroutineScope {
            articles.map {
                async {
                    // Slack 전송
                    val prefix = when (channel) {
                        NewsChannel.BREAKING -> MessageConstants.SLACK_PREFIX_BREAKING
                        NewsChannel.EXCLUSIVE -> MessageConstants.SLACK_PREFIX_EXCLUSIVE
                        NewsChannel.DEV -> MessageConstants.SLACK_PREFIX_DEV
                    }
                    val text = refiner.slackText(prefix, it.title, HashUtils.normalizeUrl(it.naverLinkHash), null) // company name is not available here
                    val sendResult = slack.send(channel, text)

                    // 전송 로그
                    persistence.insertDeliveryLog(
                        articleId = it.id!!,
                        channelName = channel.name,
                        sendResult = sendResult
                    )

                    // 스팸 키워드 기록
                    spam.recordTitleTokens(it.title)

                    sendResult.success
                }
            }.awaitAll().count { it }
        }
    }

    private suspend fun updateLastPollTime(key: String, items: List<Item>) {
        val latestPubDate = items.maxOfOrNull { refiner.pubDateToKst(it.pubDate) }
        if (latestPubDate != null) {
            runtimeStateRepo.updateState(key, latestPubDate.toString())
        }
    }
}

""