package com.news.naver.controller

import com.news.naver.data.dto.delivery.NewsDeliveryResult
import com.news.naver.data.dto.test.SendMessageRequest
import com.news.naver.data.dto.test.SendMessageResponse
import com.news.naver.data.dto.test.SendNewsRequest
import com.news.naver.data.dto.test.SendNewsByChannelRequest
import com.news.naver.data.enum.NewsChannel
import com.news.naver.service.NewsDeliveryService
import org.springframework.web.bind.annotation.*

/**
 * 테스트용 API 컨트롤러
 *
 * DB에 영향을 주지 않고 뉴스 전송 기능을 테스트할 수 있습니다.
 * curl을 사용하여 간편하게 테스트 가능합니다.
 */
@RestController
@RequestMapping("/api/test")
class TestController(
    private val newsDeliveryService: NewsDeliveryService
) {

    /**
     * 임시 메시지를 DEVELOP 채널로 전송합니다.
     *
     * 예시:
     * ```
     * curl -X POST http://localhost:3579/api/test/send-message \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "테스트 메시지입니다"}'
     * ```
     */
    @PostMapping("/send-message")
    suspend fun sendTestMessage(
        @RequestBody request: SendMessageRequest
    ): SendMessageResponse {
        val success = newsDeliveryService.sendTestMessage(
            channel = NewsChannel.DEV,
            message = request.message
        )

        return SendMessageResponse(
            success = success,
            message = if (success) "메시지가 성공적으로 전송되었습니다." else "메시지 전송에 실패했습니다.",
            channel = NewsChannel.DEV.name
        )
    }

    /**
     * 네이버 뉴스 API를 사용하여 뉴스를 가져와 DEVELOP 채널로 전송합니다.
     * DB에 저장하지 않습니다.
     *
     * 예시:
     * ```
     * # 속보 뉴스 최대 10개 전송
     * curl -X POST http://localhost:3579/api/test/send-news \
     *   -H "Content-Type: application/json" \
     *   -d '{"keyword": "속보", "maxItems": 10}'
     *
     * # 단독 뉴스 최대 10개 전송
     * curl -X POST http://localhost:3579/api/test/send-news \
     *   -H "Content-Type: application/json" \
     *   -d '{"keyword": "단독", "maxItems": 10}'
     * ```
     */
    @PostMapping("/send-news")
    suspend fun sendNews(
        @RequestBody request: SendNewsRequest
    ): NewsDeliveryResult {
        if (request.keyword != "속보" && request.keyword != "단독") {
            throw IllegalArgumentException("keyword는 '속보' 또는 '단독'이어야 합니다.")
        }

        return newsDeliveryService.deliverNews(
            channel = NewsChannel.DEV,
            query = request.keyword,
            maxItems = request.maxItems ?: 10,
            lastPollTime = null // 시간 필터링 없이 모든 뉴스 가져오기
        )
    }

    /**
     * 특정 채널의 뉴스를 전송합니다 (고급 사용)
     *
     * 예시:
     * ```
     * curl -X POST http://localhost:3579/api/test/send-news-by-channel \
     *   -H "Content-Type: application/json" \
     *   -d '{"channel": "DEV", "maxItems": 5}'
     * ```
     */
    @PostMapping("/send-news-by-channel")
    suspend fun sendNewsByChannel(
        @RequestBody request: SendNewsByChannelRequest
    ): NewsDeliveryResult {
        val channel = try {
            NewsChannel.valueOf(request.channel)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("유효하지 않은 채널입니다. 사용 가능한 채널: BREAKING, EXCLUSIVE, DEV")
        }

        return newsDeliveryService.deliverNews(
            channel = channel,
            maxItems = request.maxItems,
            lastPollTime = null
        )
    }
}
