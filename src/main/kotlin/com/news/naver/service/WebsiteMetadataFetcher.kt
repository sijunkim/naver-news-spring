package com.news.naver.service

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.regex.Pattern

@Component
class WebsiteMetadataFetcher(
    private val webClient: WebClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val ogTitlePattern = Pattern.compile(
        """<meta\s+property\s*=\s*"og:title"\s+content\s*=\s*"([^"]*)"""",
        Pattern.CASE_INSENSITIVE
    )
    private val titlePattern = Pattern.compile("""<title>([^<]*)</title>""", Pattern.CASE_INSENSITIVE)

    /**
     * 도메인을 받아 https, http 순으로 사이트 제목을 가져옵니다.
     */
    suspend fun fetchPageTitle(domain: String): String? {
        // 1. HTTPS 먼저 시도
        val titleFromHttps = fetchAndParse("https://$domain")
        if (titleFromHttps != null) {
            return titleFromHttps
        }

        // 2. HTTPS 실패 시 HTTP 시도
        return fetchAndParse("http://$domain")
    }

    /**
     * 주어진 URL로 접속하여 og:title 또는 title 태그의 내용을 파싱합니다.
     */
    private suspend fun fetchAndParse(url: String): String? {
        return try {
            logger.info("Fetching page title from: {}", url)
            val html = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String::class.java)
                .awaitSingleOrNull()

            html?.let {
                // 헬퍼 함수를 사용하여 순차적으로 파싱 및 처리
                findAndProcessTitle(ogTitlePattern, it, "og:title", url)
                    ?: findAndProcessTitle(titlePattern, it, "<title>", url)
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch or parse from {}: {}", url, e.message)
            return url
        }
    }

    /**
     * HTML에서 특정 패턴을 찾아 제목을 추출하고, 파이프(|) 문자를 처리합니다.
     */
    private fun findAndProcessTitle(pattern: Pattern, html: String, type: String, url: String): String? {
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val rawTitle = matcher.group(1)?.trim()
            if (!rawTitle.isNullOrBlank()) {
                // 파이프(|)가 있을 경우에만 분리하고, 없으면 원본 사용
                val processedTitle = if (rawTitle.contains('|')) {
                    rawTitle.split('|').first().trim()
                } else {
                    rawTitle
                }
                logger.info("Found {}: '{}' -> Processed: '{}' from {}", type, rawTitle, processedTitle, url)
                return processedTitle.takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
