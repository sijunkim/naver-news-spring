package com.news.naver.client

import com.fasterxml.jackson.annotation.JsonRootName
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText

// 네이버 뉴스 API 응답을 위한 DTO
@JsonRootName("rss")
data class NaverNewsResponse(
    @field:JacksonXmlProperty(localName = "channel")
    val channel: Channel
) {
    data class Channel(
        @field:JacksonXmlProperty(localName = "item")
        val items: List<Item> = emptyList()
    )

    data class Item(
        @field:JacksonXmlProperty(localName = "title")
        val title: String,
        @field:JacksonXmlProperty(localName = "originallink")
        val originalLink: String,
        @field:JacksonXmlProperty(localName = "link")
        val link: String,
        @field:JacksonXmlProperty(localName = "description")
        val description: String,
        @field:JacksonXmlProperty(localName = "pubDate")
        val pubDate: String
    )
}
