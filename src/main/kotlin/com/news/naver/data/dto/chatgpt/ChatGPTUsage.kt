package com.news.naver.data.dto.chatgpt

import com.fasterxml.jackson.annotation.JsonProperty

data class ChatGPTUsage(
    @field:JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @field:JsonProperty("completion_tokens")
    val completionTokens: Int,
    @field:JsonProperty("total_tokens")
    val totalTokens: Int
)
