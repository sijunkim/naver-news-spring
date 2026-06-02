package com.news.naver.data.dto.llm

/**
 * OpenAI Chat Completion 응답 본문.
 */
data class LLMResponse(
    val id: String,
    val choices: List<LLMChoice>,
    val usage: LLMUsage?
)
