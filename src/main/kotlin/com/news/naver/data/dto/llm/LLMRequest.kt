package com.news.naver.data.dto.llm

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenAI Chat Completion 요청 페이로드.
 * Groq를 포함한 OpenAI-compatible provider도 동일한 포맷을 사용한다.
 *
 * @property reasoningEffort 추론형 모델(gpt-oss 등)의 추론 강도. null 이면 필드를 보내지 않는다
 *   — 추론형이 아닌 모델(gpt-4o-mini 등)은 이 필드를 받으면 400 을 낸다.
 */
data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val temperature: Double,
    @field:JsonProperty("max_tokens")
    val maxTokens: Int,
    @field:JsonProperty("reasoning_effort")
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val reasoningEffort: String? = null
)
