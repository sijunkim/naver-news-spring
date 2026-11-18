package com.news.naver.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.news.naver.client.LLMClient
import com.news.naver.data.dto.llm.LLMChoice
import com.news.naver.data.dto.llm.LLMMessage
import com.news.naver.data.dto.llm.LLMRequest
import com.news.naver.data.dto.llm.LLMResponse
import com.news.naver.property.LLMProperties
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LLMServiceTest {

    private fun properties(reasoningEffort: String = "low") = LLMProperties(
        provider = "groq",
        providers = mapOf(
            "groq" to LLMProperties.ProviderConfig(
                apiKey = "test-key",
                apiUrl = "https://example.invalid/v1/chat/completions",
                model = "openai/gpt-oss-120b",
                reasoningEffort = reasoningEffort
            )
        )
    )

    private fun response(content: String, finishReason: String = "stop") = LLMResponse(
        id = "r1",
        choices = listOf(LLMChoice(LLMMessage("assistant", content), 0, finishReason)),
        usage = null
    )

    private val titles = listOf("정치 뉴스", "연예 뉴스", "경제 뉴스")

    @Test
    fun `번호 목록 응답은 해당 제목만 남기고 판정으로 표시한다`() {
        val service = LLMService(mock(), properties())
        val result = service.parseFilterResponse("1,3", "stop", titles)
        assertEquals(listOf("정치 뉴스", "경제 뉴스"), result.kept)
        assertTrue(result.judged)
    }

    @Test
    fun `정상 종료한 빈 응답은 남길 기사 없음으로 판정한다`() {
        val service = LLMService(mock(), properties())
        val result = service.parseFilterResponse("", "stop", listOf("연예 뉴스"))
        assertEquals(emptyList(), result.kept)
        assertTrue(result.judged)
    }

    @Test
    fun `토큰 한도로 잘린 빈 응답은 전부 통과시키고 판정으로 표시하지 않는다`() {
        val service = LLMService(mock(), properties())
        val result = service.parseFilterResponse("", "length", titles)
        assertEquals(titles, result.kept)
        assertFalse(result.judged)
    }

    @Test
    fun `호출 실패는 전부 통과시키고 판정으로 표시하지 않는다`() = runTest {
        val client = mock<LLMClient> { onBlocking { post(any()) } doThrow RuntimeException("404 model_not_found") }
        val result = LLMService(client, properties()).filterNewsTitles(titles)
        assertEquals(titles, result.kept)
        assertFalse(result.judged)
    }

    @Test
    fun `필터 요청에 provider 의 reasoning_effort 와 모델을 싣는다`() = runTest {
        val client = mock<LLMClient> { onBlocking { post(any()) } doReturn response("1") }
        LLMService(client, properties("low")).filterNewsTitles(titles)

        val captor = argumentCaptor<LLMRequest>()
        verify(client).post(captor.capture())
        assertEquals("low", captor.firstValue.reasoningEffort)
        assertEquals("openai/gpt-oss-120b", captor.firstValue.model)
    }

    @Test
    fun `reasoning_effort 를 비우면 요청 본문에서 필드를 뺀다`() = runTest {
        val client = mock<LLMClient> { onBlocking { post(any()) } doReturn response("1") }
        LLMService(client, properties("")).filterNewsTitles(titles)

        val captor = argumentCaptor<LLMRequest>()
        verify(client).post(captor.capture())
        assertNull(captor.firstValue.reasoningEffort)

        val json = jacksonObjectMapper().writeValueAsString(captor.firstValue)
        assertFalse(json.contains("reasoning_effort"), json)
    }

    @Test
    fun `reasoning_effort 가 있으면 요청 본문에 snake_case 로 들어간다`() {
        val json = jacksonObjectMapper().writeValueAsString(
            LLMRequest("m", emptyList(), 0.3, 1000, reasoningEffort = "low")
        )
        assertTrue(json.contains("\"reasoning_effort\":\"low\""), json)
        assertTrue(json.contains("\"max_tokens\":1000"), json)
    }

    @Test
    fun `gpt-oss 실제 응답 형식(reasoning 필드 포함)을 읽는다`() {
        // 2026-11-18 Groq openai/gpt-oss-120b(reasoning_effort=low) 실응답 구조를 줄인 것.
        val raw = """
            {"id":"chatcmpl-x","object":"chat.completion","created":1,"model":"openai/gpt-oss-120b",
             "choices":[{"index":0,"message":{"role":"assistant","content":"1,3","reasoning":"번호만 고른다."},
                         "logprobs":null,"finish_reason":"stop"}],
             "usage":{"prompt_tokens":334,"completion_tokens":59,"total_tokens":393,
                      "completion_tokens_details":{"reasoning_tokens":47}},
             "x_groq":{"id":"req_x"}}
        """.trimIndent()
        // Spring Boot 기본 ObjectMapper 와 같이 모르는 필드는 무시한다.
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val parsed = mapper.readValue<LLMResponse>(raw)

        assertEquals("1,3", parsed.choices.first().message.content)
        assertEquals("stop", parsed.choices.first().finishReason)
        assertEquals(393, parsed.usage?.totalTokens)
    }
}
