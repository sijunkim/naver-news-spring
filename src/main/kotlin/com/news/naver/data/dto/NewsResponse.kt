package com.news.naver.data.dto

import com.fasterxml.jackson.annotation.JsonRootName
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

/**
 * Root data class representing the RSS feed returned by the Naver News Open API.
 * Maps the top-level <rss> element and contains the channel information.
 */
@JsonRootName("rss")
data class NewsResponse(
    /**
     * Represents the <channel> element in the RSS feed, which contains metadata about the feed
     * and a list of news items.
     */
    @field:JacksonXmlProperty(localName = "channel")
    val channel: Channel
)