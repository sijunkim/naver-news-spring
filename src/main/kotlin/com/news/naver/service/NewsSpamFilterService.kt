package com.news.naver.service

import com.news.naver.property.AppProperties
import com.news.naver.repository.SpamKeywordLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NewsSpamFilterService(
    private val spamRepo: SpamKeywordLogRepository,
    private val appProperties: AppProperties
) {
    /**
     * 제목을 공백 단위 토큰으로 분해하여 각 토큰의 누적 카운트를 합산합니다.
     * 합이 threshold 초과면 스팸으로 간주합니다.
     * 정책: 길이 2 미만 토큰은 무시.
     */
    suspend fun isSpamByTitleTokens(title: String): Boolean {
        var count = 0
        val tokens = tokenize(title)

        for (t in tokens) {
            val entity = spamRepo.findFirstByKeywordAndCreatedAtAfter(t, LocalDateTime.now().minusHours(3))
            if (entity != null) {
                count += entity.count
            }
            if (count > appProperties.duplicate.threshold) return true
        }
        return false
    }

    /**
     * 스팸이든 아니든 토큰을 이벤트로 기록합니다.
     * 이미 존재하는 키워드면 count를 1 증가시키고, 없으면 새로 추가합니다.
     */
    suspend fun recordTitleTokens(title: String) {
        tokenize(title).forEach { keyword ->
            spamRepo.upsert(keyword)
        }
    }

    private fun tokenize(title: String): List<String> = title.split(Regex("\\s+")).filter { it.isNotBlank() }
}