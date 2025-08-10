package com.news.naver.service

import com.news.naver.domain.NewsArticle
import com.news.naver.repository.NewsCompanyRepository
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class NewsRefinerService(private val newsCompanyRepository: NewsCompanyRepository) {

    private var companyList: List<Pair<String, String>> = emptyList()

    suspend fun initialize() {
        companyList = newsCompanyRepository.findAll().toList()
            .map { it.domainPrefix to it.name }
            .sortedBy { it.first }
    }

    suspend fun refineHtml(text: String?): String {
        if (text == null) return "내용없음"
        return text
            .replace(Regex("(<([^>]+)>)"), "")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("`", "'")
            .replace("&apos;", "'")
    }

    suspend fun formatPubDate(pubDate: String): String {
        return try {
            val zonedDateTime = ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME)
            val koreaTime = zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            val dayOfWeekNames = arrayOf("(일)", "(월)", "(화)", "(수)", "(목)", "(금)", "(토)")
            val dayOfWeek = dayOfWeekNames[koreaTime.dayOfWeek.value % 7]
            koreaTime.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 $dayOfWeek HH:mm:ss"))
        } catch (e: Exception) {
            pubDate
        }
    }

    suspend fun extractPress(originalLink: String): String {
        if (companyList.isEmpty()) initialize()

        val address = originalLink.lowercase()
            .replace(Regex("^(https?://)?(www\\.)?(news\\.)?(view\\.)?(post\\.)?(photo\\.)?(photos\\.)?(blog\\.)?"), "")
        val domain = try { URL(originalLink).host } catch (e: Exception) { address }

        val index = searchSourceIndex(address)
        return if (index != -1) {
            companyList[index].second
        } else {
            domain.split(".").getOrNull(0) ?: "(알수없음)"
        }
    }

    private fun searchSourceIndex(address: String): Int {
        var left = 0
        var right = companyList.size - 1

        while (left <= right) {
            val mid = (left + right) / 2
            val midPrefix = companyList[mid].first
            val addressStripped = address.take(midPrefix.length)

            when {
                addressStripped == midPrefix -> return checkMoreSpecificIndex(mid, address, addressStripped)
                addressStripped < midPrefix -> right = mid - 1
                else -> left = mid + 1
            }
        }
        return -1
    }

    private fun checkMoreSpecificIndex(index: Int, address: String, addressStripped: String): Int {
        var i = index
        while (i + 1 < companyList.size && companyList[i + 1].first.startsWith(addressStripped)) {
            i++
        }

        while (i >= index) {
            if (address.startsWith(companyList[i].first)) {
                return i
            }
            i--
        }
        return -1
    }

    suspend fun createSlackPayload(news: NewsArticle, channel: com.news.naver.domain.NewsChannel): Map<String, Any> {
        val refinedTitle = refineHtml(news.title)
        val refinedDescription = refineHtml(news.summary)
        val formattedPubDate = formatPubDate(news.publishedAt.toString())

        return mapOf(
            "blocks" to listOf(
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "mrkdwn",
                        "text" to "*<${news.naverLinkHash}|${refinedTitle}>*"
                    )
                ),
                mapOf(
                    "type" to "context",
                    "elements" to listOf(
                        mapOf(
                            "type" to "plain_text",
                            "text" to "${formattedPubDate} | ${news.press}"
                        )
                    )
                ),
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "plain_text",
                        "text" to refinedDescription,
                        "emoji" to true
                    )
                ),
                mapOf("type" to "divider")
            )
        )
    }
}