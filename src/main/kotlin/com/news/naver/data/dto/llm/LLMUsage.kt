package com.news.naver.data.dto.llm

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenAI Chat Completion 응답 usage 객체.
 */
data class LLMUsage(
    @field:JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @field:JsonProperty("completion_tokens")
    val completionTokens: Int,
    @field:JsonProperty("total_tokens")
    val totalTokens: Int
)
