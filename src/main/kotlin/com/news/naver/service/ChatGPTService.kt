package com.news.naver.service

import com.news.naver.client.ChatGPTClient
import com.news.naver.data.dto.chatgpt.ChatGPTMessage
import com.news.naver.data.dto.chatgpt.ChatGPTRequest
import com.news.naver.data.dto.summary.DailyNewsItem
import com.news.naver.property.ChatGPTProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ChatGPTService(
    private val chatGPTClient: ChatGPTClient,
    private val chatGPTProperties: ChatGPTProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun filterNewsTitles(titles: List<String>): List<String> {
        if (!chatGPTProperties.isEnabled()) {
            logger.info("ChatGPT filtering disabled - all news will be delivered")
            return titles
        }

        if (titles.isEmpty()) {
            return emptyList()
        }

        val request = ChatGPTRequest(
            model = chatGPTProperties.model,
            messages = listOf(
                ChatGPTMessage(
                    role = "system",
                    content = "당신은 뉴스 필터링 전문가입니다. 광고성 뉴스와 연예 뉴스를 정확하게 구분할 수 있습니다."
                ),
                ChatGPTMessage(
                    role = "user",
                    content = buildFilterPrompt(titles)
                )
            ),
            temperature = chatGPTProperties.temperature,
            maxTokens = chatGPTProperties.maxTokens
        )

        val response = runCatching { chatGPTClient.post(request) }
            .onFailure { logger.error("Failed to filter news titles using ChatGPT API. Returning all titles.", it) }
            .getOrNull() ?: return titles

        val filteredTitles = parseFilterResponse(response.choices.firstOrNull()?.message?.content, titles)
        logger.info("ChatGPT filtered ${titles.size - filteredTitles.size} titles out of ${titles.size}")
        return filteredTitles
    }

    suspend fun generateDailySummary(newsItems: List<DailyNewsItem>): String? {
        if (!chatGPTProperties.isEnabled()) {
            logger.info("ChatGPT API key is not configured. Skipping daily summary generation.")
            return null
        }

        if (newsItems.isEmpty()) {
            logger.info("No news items to summarize.")
            return null
        }

        val request = ChatGPTRequest(
            model = chatGPTProperties.model,
            messages = listOf(
                ChatGPTMessage(
                    role = "system",
                    content = "당신은 뉴스 요약 전문가입니다. 여러 뉴스 제목과 내용을 분석하여 간결하고 명확한 요약을 작성합니다."
                ),
                ChatGPTMessage(
                    role = "user",
                    content = buildSummaryPrompt(newsItems)
                )
            ),
            temperature = chatGPTProperties.temperature,
            maxTokens = chatGPTProperties.summaryMaxTokens
        )

        val response = runCatching { chatGPTClient.post(request) }
            .onFailure { logger.error("Failed to generate daily summary using ChatGPT API.", it) }
            .getOrNull() ?: return null

        val summary = response.choices.firstOrNull()?.message?.content?.trim()

        if (summary.isNullOrBlank()) {
            logger.warn("Empty summary from ChatGPT.")
            return null
        }

        logger.info("ChatGPT generated daily summary for ${newsItems.size} news items")
        return summary
    }

    private fun buildFilterPrompt(titles: List<String>): String {
        val numberedTitles = titles.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n")

        return """
            다음은 뉴스 제목 목록입니다. 각 제목을 검토하여 광고성 뉴스나 연예 뉴스를 제외하고, 유효한 뉴스만 선택해주세요.
            
            **제외 기준:**
            - 광고성 뉴스: 특정 제품, 서비스, 기업을 홍보하는 내용
            - 연예 뉴스: 연예인, 드라마, 영화, 음악 등 연예계 관련 내용
            
            **뉴스 제목 목록:**
            $numberedTitles
            
            **응답 형식:**
            유효한 뉴스의 번호만 쉼표로 구분하여 나열해주세요. 예: 1,3,5,7
            번호만 응답하고 다른 설명은 포함하지 마세요.
        """.trimIndent()
    }

    private fun parseFilterResponse(response: String?, originalTitles: List<String>): List<String> {
        if (response.isNullOrBlank()) {
            logger.warn("Empty response from ChatGPT. Returning all titles.")
            return originalTitles
        }

        return try {
            val validIndices = response.trim()
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..originalTitles.size }
                .map { it - 1 }
                .toSet()

            originalTitles.filterIndexed { index, _ -> index in validIndices }
        } catch (e: Exception) {
            logger.error("Failed to parse ChatGPT response: $response. Returning all titles.", e)
            originalTitles
        }
    }

    private fun buildSummaryPrompt(newsItems: List<DailyNewsItem>): String {
        val newsList = newsItems.take(100).mapIndexed { index, item ->
            val summaryPart = item.summary?.let { " - $it" } ?: ""
            "${index + 1}. ${item.title}$summaryPart"
        }.joinToString("\n")

        return """
            다음은 오늘 발송된 뉴스 목록입니다. 이 뉴스들의 공통 주제와 핵심 내용을 3-5문장으로 간결하게 요약해주세요.
            
            **뉴스 목록 (최대 100개):**
            $newsList
            
            **요약 요청 사항:**
            - 5-10문장으로 간결하게 작성
            - 주요 키워드와 핵심 내용 위주로 요약
            - 500자 이내로 작성
            - 불필요한 설명 제외
        """.trimIndent()
    }
}