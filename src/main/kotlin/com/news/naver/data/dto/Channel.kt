package com.news.naver.data.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

/**
 * RSS 채널의 상세 정보를 나타내는 데이터 클래스입니다.
 *
 * @property items 뉴스 기사 목록
 */
data class Channel(
    @field:JacksonXmlProperty(localName = "item")
    val items: List<Item> = emptyList()
)
