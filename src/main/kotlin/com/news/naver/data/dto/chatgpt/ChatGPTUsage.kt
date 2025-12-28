package com.news.naver.data.dto.chatgpt

import com.fasterxml.jackson.annotation.JsonProperty

data class ChatGPTUsage(
    @JsonProperty("prompt_tokens")
    val promptTokens: Int,
    @JsonProperty("completion_tokens")
    val completionTokens: Int,
    @JsonProperty("total_tokens")
    val totalTokens: Int
)
