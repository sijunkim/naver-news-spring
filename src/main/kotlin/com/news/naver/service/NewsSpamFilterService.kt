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
     * 제목을 정규화된 토큰으로 분해한 뒤, 최근 3시간 내 동일 토큰이 등장한 횟수를 확인합니다.
     *  1) 토큰 단일 카운트가 threshold 이상이면 스팸 처리
     *  2) 매칭된 토큰 개수의 합이 threshold 이상이면 스팸 처리
     * 정책: 길이 2 미만 토큰은 무시, 동일 기사 내 중복 토큰은 1회만 집계
     */
    suspend fun isSpamByTitleTokens(title: String): Boolean {
        val tokens = tokenize(title)
        if (tokens.isEmpty()) return false

        var matchedKeywordCount = 0
        val windowStart = LocalDateTime.now().minusHours(3)

        for (token in tokens) {
            val entity = spamRepo.findFirstByKeywordAndCreatedAtAfter(token, windowStart) ?: continue

            if (entity.count >= appProperties.duplicate.threshold) return true

            matchedKeywordCount++
            if (matchedKeywordCount >= appProperties.duplicate.threshold) return true
        }
        return false
    }

    /**
     * 스팸이든 아니든 토큰을 이벤트로 기록합니다.
     * 이미 존재하는 키워드면 count를 1 증가시키고, 없으면 새로 추가합니다.
     */
    suspend fun recordTitleTokens(title: String) {
        val tokens = tokenize(title)
        if (tokens.isEmpty()) return

        tokens.forEach { keyword ->
            spamRepo.upsert(keyword)
        }
    }

    private fun tokenize(title: String): List<String> =
        title
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
}
