package com.news.naver.util.formatter

import com.news.naver.data.dto.News
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class SlackMessageFormatter {

    fun createPayload(news: News): Map<String, Any?> {
        return mapOf(
            "text" to news.title,
            "blocks" to listOf(
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "mrkdwn",
                        "text" to "*<${news.link}|${news.title}>*"
                    )
                ),
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "plain_text",
                        "text" to (news.description ?: "내용없음"),
                        "emoji" to true
                    )
                ),
                mapOf(
                    "type" to "context",
                    "elements" to listOf(
                        mapOf(
                            "type" to "plain_text",
                            "text" to "${formatKoreanDateTime(news.pubDate!!)} | ${news.company}"
                        )
                    )
                ),
                mapOf(
                    "type" to "divider"
                )
            )
        )
    }

    fun formatKoreanDateTime(dateString: String): String =
        ZonedDateTime.parse(dateString, DateTimeFormatter.RFC_1123_DATE_TIME)
            .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (EEEE) h:mm:ss a", Locale.KOREAN))
}
