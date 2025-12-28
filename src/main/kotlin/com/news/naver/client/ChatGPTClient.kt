package com.news.naver.client

import com.news.naver.data.dto.chatgpt.ChatGPTMessage
import com.news.naver.data.dto.chatgpt.ChatGPTRequest
import com.news.naver.data.dto.chatgpt.ChatGPTResponse
import com.news.naver.data.dto.summary.DailyNewsItem
import com.news.naver.property.ChatGPTProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ChatGPTClient(
    private val webClient: WebClient,
    private val chatGPTProperties: ChatGPTProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CHATGPT_API_URL = "https://api.openai.com/v1/chat/completions"
    }

    /**
     * ChatGPT API를 호출하여 뉴스 제목들을 배치로 필터링합니다.
     *
     * @param titles 필터링할 뉴스 제목 리스트
     * @return 필터링을 통과한 뉴스 제목 리스트
     */
    suspend fun filterNewsTitles(titles: List<String>): List<String> {
        if (!chatGPTProperties.isEnabled()) {
            logger.info("ChatGPT filtering disabled - all news will be delivered")
            return titles
        }

        if (titles.isEmpty()) {
            return emptyList()
        }

        try {
            val prompt = buildFilterPrompt(titles)
            val request = ChatGPTRequest(
                messages = listOf(
                    ChatGPTMessage(
                        role = "system",
                        content = "당신은 뉴스 필터링 전문가입니다. 광고성 뉴스와 연예 뉴스를 정확하게 구분할 수 있습니다."
                    ),
                    ChatGPTMessage(
                        role = "user",
                        content = prompt
                    )
                )
            )

            val response = webClient.post()
                .uri(CHATGPT_API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${chatGPTProperties.apiKey}")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatGPTResponse::class.java)
                .awaitSingle()

            val filteredTitles = parseFilterResponse(response.choices.firstOrNull()?.message?.content, titles)

            logger.info("ChatGPT filtered ${titles.size - filteredTitles.size} titles out of ${titles.size}")

            return filteredTitles
        } catch (e: Exception) {
            logger.error("Failed to filter news titles using ChatGPT API. Returning all titles.", e)
            return titles
        }
    }

    /**
     * 필터링 프롬프트를 생성합니다.
     */
    private fun buildFilterPrompt(titles: List<String>): String {
        val numberedTitles = titles.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n")

        return """
다음은 뉴스 제목 목록입니다. 각 제목을 검토하여 광고성 뉴스나 연예 뉴스를 제외하고, 유효한 뉴스만 선택해주세요.

**제외 기준:**
- 광고성 뉴스: 특정 제품, 서비스, 기업을 홍보하는 내용
- 연예 뉴스: 연예인, 드라마, 영화, 음악 등 연예계 관련 내용
- 스포츠 뉴스: 스포츠 경기, 선수 관련 내용

**뉴스 제목 목록:**
$numberedTitles

**응답 형식:**
유효한 뉴스의 번호만 쉼표로 구분하여 나열해주세요. 예: 1,3,5,7
번호만 응답하고 다른 설명은 포함하지 마세요.
        """.trimIndent()
    }

    /**
     * ChatGPT 응답을 파싱하여 필터링을 통과한 제목 리스트를 반환합니다.
     */
    private fun parseFilterResponse(response: String?, originalTitles: List<String>): List<String> {
        if (response.isNullOrBlank()) {
            logger.warn("Empty response from ChatGPT. Returning all titles.")
            return originalTitles
        }

        try {
            // 응답에서 숫자만 추출 (1,3,5,7 형식)
            val validIndices = response.trim()
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..originalTitles.size }
                .map { it - 1 } // 0-based index로 변환
                .toSet()

            return originalTitles.filterIndexed { index, _ -> index in validIndices }
        } catch (e: Exception) {
            logger.error("Failed to parse ChatGPT response: $response. Returning all titles.", e)
            return originalTitles
        }
    }

    /**
     * 일일 뉴스 요약을 생성합니다
     *
     * @param newsItems 요약할 뉴스 목록
     * @return 요약 문자열 (실패 시 null)
     */
    suspend fun generateDailySummary(newsItems: List<DailyNewsItem>): String? {
        if (!chatGPTProperties.isEnabled()) {
            logger.info("ChatGPT API key is not configured. Skipping daily summary generation.")
            return null
        }

        if (newsItems.isEmpty()) {
            logger.info("No news items to summarize.")
            return null
        }

        try {
            val prompt = buildSummaryPrompt(newsItems)
            val request = ChatGPTRequest(
                messages = listOf(
                    ChatGPTMessage(
                        role = "system",
                        content = "당신은 뉴스 요약 전문가입니다. 여러 뉴스 제목과 내용을 분석하여 간결하고 명확한 요약을 작성합니다."
                    ),
                    ChatGPTMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                maxTokens = 500
            )

            val response = webClient.post()
                .uri(CHATGPT_API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${chatGPTProperties.apiKey}")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatGPTResponse::class.java)
                .awaitSingle()

            val summary = response.choices.firstOrNull()?.message?.content?.trim()

            if (summary.isNullOrBlank()) {
                logger.warn("Empty summary from ChatGPT.")
                return null
            }

            logger.info("ChatGPT generated daily summary for ${newsItems.size} news items")
            return summary
        } catch (e: Exception) {
            logger.error("Failed to generate daily summary using ChatGPT API.", e)
            return null
        }
    }

    /**
     * 일일 요약 프롬프트를 생성합니다
     */
    private fun buildSummaryPrompt(newsItems: List<DailyNewsItem>): String {
        val newsList = newsItems.take(20).mapIndexed { index, item ->
            val summaryPart = item.summary?.let { " - $it" } ?: ""
            "${index + 1}. ${item.title}$summaryPart"
        }.joinToString("\n")

        return """
다음은 오늘 발송된 뉴스 목록입니다. 이 뉴스들의 공통 주제와 핵심 내용을 3-5문장으로 간결하게 요약해주세요.

**뉴스 목록 (최대 20개):**
$newsList

**요약 요청 사항:**
- 3-5문장으로 간결하게 작성
- 주요 키워드와 핵심 내용 위주로 요약
- 200자 이내로 작성
- 불필요한 설명 제외
        """.trimIndent()
    }
}
