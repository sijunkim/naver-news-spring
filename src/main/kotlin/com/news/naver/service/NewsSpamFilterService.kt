package com.news.naver.service

import com.news.naver.repository.SpamKeywordLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NewsSpamFilterService(
    private val spamRepo: SpamKeywordLogRepository
) {
    /**
     * 제목을 공백 단위 토큰으로 분해하여 최근 windowMinutes 내 등장 빈도를 합산.
     * 합이 threshold 초과면 스팸으로 간주합니다.
     * 정책: 길이 2 미만 토큰은 무시.
     */
    suspend fun isSpamByTitleTokens(
        title: String,
        threshold: Int,
        windowMinutes: Long = 120
    ): Boolean {
        val now = LocalDateTime.now()
        val since = now.minusMinutes(windowMinutes)
        var count = 0
        val tokens = tokenize(title)

        for (t in tokens) {
            val c = spamRepo.countSpamKeywordLogByKeywordSince(t, since)
            count += c.toInt()
            if (count > threshold) return true
        }
        return false
    }

    /**
     * 스팸이든 아니든 토큰을 이벤트로 기록(윈도우 집계를 위한 데이터 축적)
     */
    suspend fun recordTitleTokens(title: String) {
        val now = LocalDateTime.now()
        tokenize(title).forEach { t ->
            spamRepo.insertSpamKeywordLog(t, title, now)
        }
    }

    private fun tokenize(title: String): List<String> =
        title.replace(" ", " ")
            .split(" ", "·", "—", "-", "\t", "\n", "\r")
            .map { it.trim() }
            .filter { it.length >= 2 }
}