package com.news.naver.data.dto.llm

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenAI Chat Completion 응답 choices 항목.
 */
data class LLMChoice(
    val message: LLMMessage,
    val index: Int,
    @field:JsonProperty("finish_reason")
    val finishReason: String?
)
