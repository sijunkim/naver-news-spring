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
     * 제목 필터 결과.
     *
     * @property kept 통과시킨 제목
     * @property judged LLM 이 실제로 판정했는지. false 면 비활성·호출 실패·응답 잘림으로 인한
     *   "전부 통과" 폴백이다 — 호출자는 이 결과를 판정으로 기억(캐시)하면 안 된다.
     */
    data class TitleFilterResult(val kept: List<String>, val judged: Boolean)

    /**
     * 뉴스 제목 목록에서 연예 뉴스를 LLM으로 걸러낸다.
     * LLM 비활성/오류 시 원본을 그대로 반환한다(judged=false).
     */
    suspend fun filterNewsTitles(titles: List<String>): TitleFilterResult {
        if (!llmProperties.isEnabled()) {
            logger.info("LLM filtering disabled - all news will be delivered")
            return TitleFilterResult(titles, judged = false)
        }

        if (titles.isEmpty()) {
            return TitleFilterResult(emptyList(), judged = true)
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
            maxTokens = llmProperties.maxTokens,
            reasoningEffort = llmProperties.active.reasoningEffortOrNull
        )

        val response = runCatching { llmClient.post(request) }
            .onFailure { logger.error("Failed to filter news titles using LLM API. Returning all titles.", it) }
            .getOrNull() ?: return TitleFilterResult(titles, judged = false)

        val choice = response.choices.firstOrNull()
        val result = parseFilterResponse(choice?.message?.content, choice?.finishReason, titles)
        if (result.judged) {
            logger.info("LLM filtered ${titles.size - result.kept.size} titles out of ${titles.size}")
        }
        return result
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
            maxTokens = llmProperties.summaryMaxTokens,
            reasoningEffort = llmProperties.active.reasoningEffortOrNull
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

    /**
     * 번호 목록 응답을 제목으로 되돌린다.
     *
     * 빈 응답은 finish_reason 으로 구분한다.
     * - `stop`: 모델이 정상 종료하며 "남길 번호 없음"으로 답한 것 → 전부 제외(판정).
     *   gpt-oss 는 목록이 전부 연예 뉴스면 빈 문자열을 돌려준다(2026-11-18 실측 4/4건).
     * - 그 외(`length` 등): 추론에 토큰을 다 써 답을 못 낸 것 → 전부 통과(폴백, 미판정).
     */
    internal fun parseFilterResponse(
        response: String?,
        finishReason: String?,
        originalTitles: List<String>
    ): TitleFilterResult {
        if (response.isNullOrBlank()) {
            return if (finishReason == "stop") {
                TitleFilterResult(emptyList(), judged = true)
            } else {
                logger.warn("Empty response from LLM (finish_reason=$finishReason). Returning all titles.")
                TitleFilterResult(originalTitles, judged = false)
            }
        }

        return try {
            val validIndices = response.trim()
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..originalTitles.size }
                .map { it - 1 }
                .toSet()

            TitleFilterResult(originalTitles.filterIndexed { index, _ -> index in validIndices }, judged = true)
        } catch (e: Exception) {
            logger.error("Failed to parse LLM response: $response. Returning all titles.", e)
            TitleFilterResult(originalTitles, judged = false)
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
