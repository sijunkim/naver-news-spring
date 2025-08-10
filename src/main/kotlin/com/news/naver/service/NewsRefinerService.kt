package com.news.naver.service

import com.news.naver.data.constant.MessageConstants
import com.news.naver.data.constant.RegexConstants
import com.news.naver.data.constant.TimeConstants
import com.news.naver.entity.NewsArticleEntity
import com.news.naver.repository.NewsCompanyCustomRepository
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 뉴스 데이터를 정제하고 슬랙 메시지 페이로드를 생성하는 서비스 클래스입니다.
 * NestJS 프로젝트의 `NewsRefiner` 역할을 Kotlin 코루틴 기반으로 재구현했습니다.
 *
 * @property newsCompanyRepository 언론사 정보를 조회하기 위한 리포지토리
 */
@Service
class NewsRefinerService(private val newsCompanyCustomRepository: NewsCompanyCustomRepository) {

    /**
     * 언론사 목록을 캐싱하기 위한 내부 변수입니다.
     */
    private var companyList: List<Pair<String, String>> = emptyList()

    /**
     * 서비스 초기화 시 언론사 목록을 데이터베이스에서 로드하여 캐싱합니다.
     * 언론사 추출 로직의 효율성을 높이기 위해 사용됩니다.
     */
    suspend fun initialize() {
        companyList = newsCompanyCustomRepository.selectNewsCompanyAll()
            .map { it.domainPrefix to it.name }
            .sortedBy { it.first }
    }

    /**
     * HTML 태그 및 특정 HTML 엔티티를 일반 텍스트로 정제합니다.
     * NestJS의 `htmlParsingToText` 로직을 따릅니다.
     *
     * @param text 정제할 원본 문자열 (HTML 포함 가능)
     * @return HTML 태그와 엔티티가 제거된 정제된 텍스트
     */
    suspend fun refineHtml(text: String?): String {
        if (text == null) return MessageConstants.NO_CONTENT_AVAILABLE
        return text
            .replace(Regex(RegexConstants.HTML_TAG_REGEX), "")
            .replace("&quot;", """)
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("`", "'")
            .replace("&apos;", "'")
    }

    /**
     * 주어진 발행일(pubDate) 문자열을 한국 시간대에 맞춰 포맷팅합니다.
     * NestJS의 `pubDateToKoreaTime` 로직을 따릅니다.
     *
     * @param pubDate RFC_1123_DATE_TIME 형식의 발행일 문자열
     * @return "yyyy년 MM월 dd일 (요일) HH:mm:ss" 형식의 한국 시간 문자열
     */
    suspend fun formatPubDate(pubDate: String): String {
        return try {
            val zonedDateTime = ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME)
            val koreaTime = zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            val dayOfWeekNames = arrayOf("(일)", "(월)", "(화)", "(수)", "(목)", "(금)", "(토)")
            val dayOfWeek = dayOfWeekNames[koreaTime.dayOfWeek.value % 7]
            koreaTime.format(DateTimeFormatter.ofPattern(TimeConstants.KOREAN_DATE_TIME_FORMAT))
        } catch (e: Exception) {
            pubDate
        }
    }

    /**
     * 원본 링크에서 언론사 이름을 추출합니다.
     * NestJS의 `substractComapny` 로직을 Kotlin으로 재구현했으며, DB에 저장된 언론사 목록을 활용합니다.
     * 언론사 목록은 `initialize` 함수를 통해 미리 로드됩니다.
     *
     * @param originalLink 기사의 원본 링크
     * @return 추출된 언론사 이름 또는 "(알수없음)"
     */
    suspend fun extractPress(originalLink: String): String {
        if (companyList.isEmpty()) initialize()

        val address = originalLink.lowercase()
            .replace(Regex(RegexConstants.URL_PREFIX_REGEX), "")
        val domain = try { URL(originalLink).host } catch (e: Exception) { address }

        val index = searchSourceIndex(address)
        return if (index != -1) {
            companyList[index].second
        } else {
            domain.split(".").getOrNull(0) ?: MessageConstants.UNKNOWN_PRESS
        }
    }

    /**
     * 언론사 목록에서 주어진 주소(address)에 해당하는 언론사의 인덱스를 이진 탐색 방식으로 찾습니다.
     * NestJS의 `searchSourceIndex` 로직을 따릅니다.
     *
     * @param address 검색할 주소 문자열
     * @return 언론사 목록에서의 인덱스, 찾지 못하면 -1
     */
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

    /**
     * 이진 탐색 후, 더 정확한 언론사 인덱스를 확인합니다.
     * NestJS의 `checkSourceIndex` 로직을 따릅니다.
     *
     * @param index 이진 탐색으로 찾은 초기 인덱스
     * @param address 원본 주소 문자열
     * @param addressStripped 주소에서 접두사가 제거된 문자열
     * @return 최종 언론사 목록에서의 인덱스
     */
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

    /**
     * Slack 메시지 페이로드를 Block Kit 형식으로 생성합니다.
     * NestJS의 `getRefineNews` 로직을 따르며, 가독성 높은 메시지를 구성합니다.
     *
     * @param news 전송할 뉴스 기사 엔티티
     * @param channel 뉴스 채널 (속보, 단독 등)
     * @return Slack Webhook으로 전송할 Map 형태의 페이로드
     */
    suspend fun createSlackPayload(news: NewsArticleEntity, channel: com.news.naver.data.enum.NewsChannel): Map<String, Any> {
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