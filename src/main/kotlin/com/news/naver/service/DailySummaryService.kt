package com.news.naver.service

import com.news.naver.client.SlackClient
import com.news.naver.data.dto.summary.DailyNewsItem
import com.news.naver.data.enums.NewsChannel
import com.news.naver.repository.NewsArticleRepository
import com.news.naver.util.atStartOfDayKST
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class DailySummaryService(
    private val newsArticleRepository: NewsArticleRepository,
    private val llmService: LLMService,
    private val slackClient: SlackClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // 불용어 목록 (조사, 어미, 검색 키워드 등)
        private val STOP_WORDS = setOf(
            "은", "는", "이", "가", "을", "를", "의", "에", "와", "과", "도", "로", "으로", "에서",
            "한", "할", "하다", "있다", "되다", "것", "등", "및", "더", "수", "위해", "통해",
            "대해", "따르면", "따라", "위한", "통한", "대한",
            "속보", "단독"  // 검색 키워드 제외
        )
    }

    /**
     * 일일 뉴스 요약을 생성하고 Slack으로 전송합니다
     *
     * @param date 대상 날짜
     */
    suspend fun generateAndSendDailySummary(date: LocalDate) {
        logger.info("Generating daily summary for date: $date")

        // 1. 해당 날짜의 발송 뉴스 조회
        val startDateTime = date.atStartOfDayKST()
        val endDateTime = date.plusDays(1).atStartOfDayKST()
        val newsItems = newsArticleRepository.selectDeliveredNewsInDateRange(startDateTime, endDateTime)

        if (newsItems.isEmpty()) {
            logger.info("No news delivered on $date. Skipping daily summary.")
            return
        }

        logger.info("Found ${newsItems.size} unique news articles delivered on $date")

        // 2. LLM으로 요약 생성
        val summary = try {
            llmService.generateDailySummary(newsItems)
        } catch (e: Exception) {
            logger.error("Failed to generate summary via LLM", e)
            null
        }

        // 3. 키워드 TOP 20 추출
        val topKeywords = extractTopKeywords(newsItems, 20)

        // 4. Slack 알림 발송
        sendDailySummary(date, summary, topKeywords, newsItems.size)
    }

    /**
     * 뉴스 제목에서 키워드를 추출하고 빈도수 계산
     *
     * @param newsItems 뉴스 목록
     * @param topN 상위 N개 키워드
     * @return 키워드와 빈도수 리스트 (내림차순)
     */
    fun extractTopKeywords(newsItems: List<DailyNewsItem>, topN: Int = 10): List<Pair<String, Int>> {
        // 한글 2자 이상 추출
        val koreanWordRegex = Regex("[가-힣]{2,}")

        val keywordFrequency = newsItems
            .flatMap { item ->
                koreanWordRegex.findAll(item.title).map { it.value }.toList()
            }
            .filterNot { it in STOP_WORDS } // 불용어 제거
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(topN)

        return keywordFrequency
    }

    /**
     * Slack으로 일일 요약을 전송합니다
     *
     * @param date 대상 날짜
     * @param summary LLM 요약 (null이면 "요약 생성 실패")
     * @param topKeywords TOP 20 키워드
     * @param uniqueArticleCount 고유 뉴스 수
     */
    private suspend fun sendDailySummary(
        date: LocalDate,
        summary: String?,
        topKeywords: List<Pair<String, Int>>,
        uniqueArticleCount: Int
    ) {
        val dateString = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val summarySection = if (summary != null) {
            "📝 *요약:*\n$summary"
        } else {
            "📝 *요약:*\n요약 생성 실패 (LLM API 미설정 또는 요청 실패)"
        }

        val keywordsSection = if (topKeywords.isNotEmpty()) {
            val keywordList = topKeywords.mapIndexed { index, (keyword, count) ->
                "${index + 1}. $keyword (${count}회)"
            }.joinToString("\n")
            "🔑 *TOP 20 키워드:*\n$keywordList"
        } else {
            "🔑 *TOP 20 키워드:*\n키워드 없음"
        }

        val message = """
            📊 *일일 뉴스 발송 리포트 ($dateString)*

            ✅ *발송 건수:* ${uniqueArticleCount}건

            $summarySection

            $keywordsSection
        """.trimIndent()

        val payload = mapOf("text" to message)

        try {
            val result = slackClient.send(NewsChannel.BREAKING, payload)

            if (result.success) {
                logger.info("Daily summary sent successfully to Slack")
            } else {
                logger.error("Failed to send daily summary to Slack. HTTP Status: ${result.httpStatus}")
            }
        } catch (e: Exception) {
            logger.error("Error while sending daily summary to Slack", e)
        }
    }
}
