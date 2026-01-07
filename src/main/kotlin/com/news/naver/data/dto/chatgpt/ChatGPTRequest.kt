package com.news.naver.data.dto.chatgpt

import com.fasterxml.jackson.annotation.JsonProperty

data class ChatGPTRequest(
    val model: String,
    val messages: List<ChatGPTMessage>,
    val temperature: Double,
    @field:JsonProperty("max_tokens")
    val maxTokens: Int
)
