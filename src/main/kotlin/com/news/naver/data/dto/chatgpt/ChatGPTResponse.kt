package com.news.naver.data.dto.chatgpt

data class ChatGPTResponse(
    val id: String,
    val choices: List<ChatGPTChoice>,
    val usage: ChatGPTUsage?
)
