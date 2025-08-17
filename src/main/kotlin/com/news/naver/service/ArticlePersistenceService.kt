package com.news.naver.service

import com.news.naver.client.SlackClient
import com.news.naver.data.enum.DeliveryStatus
import com.news.naver.entity.NewsArticleEntity
import com.news.naver.repository.DeliveryLogRepository
import com.news.naver.repository.NewsArticleRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ArticlePersistenceService(
    private val articleRepo: NewsArticleRepository,
    private val deliveryRepo: DeliveryLogRepository
) {
    /**
     * 새로운 기사 목록을 DB에 일괄 저장합니다.
     */
    suspend fun insertBulkArticles(articles: List<NewsArticleEntity>): Long {
        return articleRepo.insertBulkArticles(articles)
    }

    /**
     * 해시 목록을 기반으로 DB에서 기사 목록을 조회합니다.
     */
    suspend fun selectArticlesByHashes(hashes: List<String>): List<NewsArticleEntity> {
        // R2DBC에서 IN절 파라미터 바인딩이 불안정할 수 있어, 개별 조회로 우선 구현
        return hashes.mapNotNull { articleRepo.selectNewsArticleByHash(it) }
    }

    /**
     * 슬랙 전송 결과를 기반으로 전송 로그를 DB에 기록합니다.
     */
    suspend fun insertDeliveryLog(
        articleId: Long,
        channelName: String,
        sendResult: SlackClient.SlackSendResult
    ) {
        deliveryRepo.insertDeliveryLog(
            articleId = articleId,
            channel = channelName,
            status = if (sendResult.success) DeliveryStatus.SUCCESS.name else DeliveryStatus.FAILED.name,
            httpStatus = sendResult.httpStatus,
            sentAt = LocalDateTime.now(),
            responseBody = sendResult.body
        )
    }
}
