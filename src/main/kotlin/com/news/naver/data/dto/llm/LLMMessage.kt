package com.news.naver.data.dto.llm

/**
 * OpenAI Chat Completion 와이어 포맷의 message 객체.
 * Groq를 포함한 OpenAI-compatible provider도 동일한 포맷을 사용한다.
 */
data class LLMMessage(
    val role: String,
    val content: String
)
