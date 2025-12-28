package com.news.naver.data.dto.chatgpt

import com.fasterxml.jackson.annotation.JsonProperty

data class ChatGPTRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatGPTMessage>,
    val temperature: Double = 0.3,
    @JsonProperty("max_tokens")
    val maxTokens: Int = 1000
)
