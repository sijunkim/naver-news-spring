package com.news.naver.data.dto.llm

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenAI Chat Completion 요청 페이로드.
 * Groq를 포함한 OpenAI-compatible provider도 동일한 포맷을 사용한다.
 */
data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val temperature: Double,
    @field:JsonProperty("max_tokens")
    val maxTokens: Int
)
