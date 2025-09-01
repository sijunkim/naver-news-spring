package com.news.naver.service

import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import org.springframework.stereotype.Service

@Service
class NewsRefinerService {

    private val htmlTagRegex = Regex("<[^>]*>")

    fun refineTitle(raw: String?): String =
        raw?.replace(htmlTagRegex, "")?.replace("&quot;", "\"")?.trim().orEmpty()

    fun refineDescription(raw: String?): String? =
        raw?.replace(htmlTagRegex, "")
            ?.replace("&quot;", "")
            ?.replace("&amp;", "&")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.replace("&nbsp;", " ")
            ?.trim().orEmpty()

    /**
     * 네이버 RSS pubDate → KST(Asia/Seoul) 로 변환 (형식: RFC_1123)
     */
    fun pubDateToKst(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val zdt = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
        return zdt.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    /**
     * originalLink 에서 도메인(언론사 후보) 추출
     */
    fun extractCompany(originalLink: String?): String? {
        val target = originalLink ?: return null
        return try {
            val host = URI(target).host ?: return null
            host.lowercase()
                .removePrefix("www.")
                .removePrefix("m.")
        } catch (_: Exception) { null }
    }
}