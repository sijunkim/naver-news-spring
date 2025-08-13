package com.news.naver.data.dto

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

/**
 * 개별 뉴스 기사 아이템의 상세 정보를 나타내는 데이터 클래스입니다.
 *
 * @property title 기사 제목
 * @property originalLink 기사의 원본 링크
 * @property link 기사의 네이버 링크
 * @property description 기사 내용 요약
 * @property pubDate 기사 발행일시 (RFC 1123 형식)
 */
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
