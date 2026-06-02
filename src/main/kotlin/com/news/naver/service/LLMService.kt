package com.news.naver.service

import com.news.naver.client.LLMClient
import com.news.naver.data.dto.llm.LLMMessage
import com.news.naver.data.dto.llm.LLMRequest
import com.news.naver.data.dto.summary.DailyNewsItem
import com.news.naver.property.LLMProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LLMService(
    private val llmClient: LLMClient,
    private val llmProperties: LLMProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 뉴스 제목 목록에서 연예 뉴스를 LLM으로 걸러낸다.
     * LLM 비활성/오류 시 원본을 그대로 반환한다.
     */
    suspend fun filterNewsTitles(titles: List<String>): List<String> {
        if (!llmProperties.isEnabled()) {
            logger.info("LLM filtering disabled - all news will be delivered")
            return titles
        }

        if (titles.isEmpty()) {
            return emptyList()
        }

        val request = LLMRequest(
            model = llmProperties.active.model,
            messages = listOf(
                LLMMessage(
                    role = "system",
                    content = "당신은 뉴스 필터링 전문가입니다. 광고성 뉴스와 연예 뉴스를 정확하게 구분할 수 있습니다."
                ),
                LLMMessage(
                    role = "user",
                    content = buildFilterPrompt(titles)
                )
            ),
            temperature = llmProperties.temperature,
            maxTokens = llmProperties.maxTokens
        )

        val response = runCatching { llmClient.post(request) }
            .onFailure { logger.error("Failed to filter news titles using LLM API. Returning all titles.", it) }
            .getOrNull() ?: return titles

        val filteredTitles = parseFilterResponse(response.choices.firstOrNull()?.message?.content, titles)
        logger.info("LLM filtered ${titles.size - filteredTitles.size} titles out of ${titles.size}")
        return filteredTitles
    }

    /**
     * 하루 동안 발송된 뉴스 목록을 한 단락으로 요약한다.
     */
    suspend fun generateDailySummary(newsItems: List<DailyNewsItem>): String? {
        if (!llmProperties.isEnabled()) {
            logger.info("LLM API key is not configured. Skipping daily summary generation.")
            return null
        }

        if (newsItems.isEmpty()) {
            logger.info("No news items to summarize.")
            return null
        }

        val request = LLMRequest(
            model = llmProperties.active.model,
            messages = listOf(
                LLMMessage(
                    role = "system",
                    content = "당신은 뉴스 요약 전문가입니다. 여러 뉴스 제목과 내용을 분석하여 간결하고 명확한 요약을 작성합니다."
                ),
                LLMMessage(
                    role = "user",
                    content = buildSummaryPrompt(newsItems)
                )
            ),
            temperature = llmProperties.temperature,
            maxTokens = llmProperties.summaryMaxTokens
        )

        val response = runCatching { llmClient.post(request) }
            .onFailure { logger.error("Failed to generate daily summary using LLM API.", it) }
            .getOrNull() ?: return null

        val summary = response.choices.firstOrNull()?.message?.content?.trim()

        if (summary.isNullOrBlank()) {
            logger.warn("Empty summary from LLM.")
            return null
        }

        logger.info("LLM generated daily summary for ${newsItems.size} news items")
        return summary
    }

    private fun buildFilterPrompt(titles: List<String>): String {
        val numberedTitles = titles.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n")

        return """
            다음은 뉴스 제목 목록입니다. 각 제목을 검토하여 연예 뉴스만 제외하고, 유효한 뉴스를 선택해주세요.

            **제외 기준:**
            - 연예 뉴스: 연예인, 드라마, 영화, 음악, K-POP 등 연예계 관련 내용

            **포함 기준:**
            - 정치, 경제, 사회, 과학, 기술, 스포츠 등 모든 일반 뉴스
            - 광고성 뉴스도 포함

            **뉴스 제목 목록:**
            $numberedTitles

            **응답 형식:**
            유효한 뉴스의 번호만 쉼표로 구분하여 나열해주세요. 예: 1,3,5,7
            번호만 응답하고 다른 설명은 포함하지 마세요.
        """.trimIndent()
    }

    private fun parseFilterResponse(response: String?, originalTitles: List<String>): List<String> {
        if (response.isNullOrBlank()) {
            logger.warn("Empty response from LLM. Returning all titles.")
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
            logger.error("Failed to parse LLM response: $response. Returning all titles.", e)
            originalTitles
        }
    }

    private fun buildSummaryPrompt(newsItems: List<DailyNewsItem>): String {
        val newsList = newsItems.mapIndexed { index, item ->
            val summaryPart = item.summary?.let { " - $it" } ?: ""
            "${index + 1}. ${item.title}$summaryPart"
        }.joinToString("\n")

        return """
            다음은 오늘 발송된 모든 뉴스 목록입니다. 이 뉴스들을 주제별로 분류하고, 중복된 내용을 제거하여 핵심 내용을 요약해주세요.

            **뉴스 목록 (총 ${newsItems.size}개):**
            $newsList

            **요약 요청 사항:**
            - 주제별로 그룹화하여 요약 (예: 정치, 경제, 사회, 기술, 스포츠 등)
            - 중복되거나 유사한 뉴스는 하나로 통합
            - 각 주제별로 최대 5문장으로 핵심 내용 요약
            - 전체 1000자 이내로 작성
            - 주제 별로 내용 구분하여 번호로 표시하고 마지막에 개행 문자(\n) 추가
            - 불필요한 설명 제외
        """.trimIndent()
    }
}
