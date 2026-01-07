package com.news.naver.data.dto.chatgpt

import com.fasterxml.jackson.annotation.JsonProperty

data class ChatGPTChoice(
    val message: ChatGPTMessage,
    val index: Int,
    @field:JsonProperty("finish_reason")
    val finishReason: String?
)
