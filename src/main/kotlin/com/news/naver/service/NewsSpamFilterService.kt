package com.news.naver.service

import com.news.naver.data.constant.RegexConstants
import com.news.naver.data.constant.TimeConstants
import com.news.naver.entity.SpamKeywordLogEntity
import com.news.naver.property.AppProperties
import com.news.naver.repository.SpamKeywordLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 뉴스 제목의 키워드를 기반으로 스팸성 뉴스를 필터링하고 관리하는 서비스 클래스입니다.
 * NestJS 프로젝트의 키워드 기반 중복 체크 로직을 DB 기반으로 재구현했습니다.
 *
 * @property spamKeywordLogRepository 스팸 키워드 로그 데이터에 접근하기 위한 리포지토리
 * @property appProperties 애플리케이션 관련 설정 (중복 임계치 등)
 */
@Service
class NewsSpamFilterService(
    private val spamKeywordLogRepository: SpamKeywordLogRepository,
    private val appProperties: AppProperties
) {

    /**
     * 주어진 뉴스 제목이 스팸으로 분류될 수 있는지 확인합니다.
     * 제목에서 키워드를 추출하여 기존에 기록된 스팸 키워드 로그와 비교합니다.
     *
     * @param newsTitle 확인할 뉴스 제목
     * @return 스팸으로 분류되면 true, 아니면 false
     */
    suspend fun isSpam(newsTitle: String): Boolean {
        val keywords = extractKeywords(newsTitle)
        if (keywords.isEmpty()) return false

        val duplicatedCount = spamKeywordLogRepository.countSpamKeywordLogByKeywordIn(keywords)
        return duplicatedCount > appProperties.duplicate.threshold
    }

    /**
     * 뉴스 제목에서 키워드를 추출하여 스팸 키워드 로그에 기록합니다.
     * 이 키워드들은 향후 스팸 필터링에 사용됩니다.
     *
     * @param newsTitle 키워드를 기록할 뉴스 제목
     */
    suspend fun recordKeywords(newsTitle: String) {
        val keywords = extractKeywords(newsTitle)
        val logs = keywords.map { SpamKeywordLogEntity(keyword = it) }
        spamKeywordLogRepository.saveAll(logs).collect { /* consume the flow */ }
    }

    /**
     * 일정 시간(2시간)이 지난 오래된 스팸 키워드 로그를 데이터베이스에서 삭제합니다.
     * NestJS의 `makeKeywordFilesCron` 로직을 DB 기반으로 재구현한 것입니다.
     */
    suspend fun cleanupOldKeywords() {
        // 2시간 이전의 키워드 로그 삭제
        val twoHoursAgo = LocalDateTime.now().minusHours(TimeConstants.SPAM_KEYWORD_CLEANUP_HOURS)
        spamKeywordLogRepository.deleteSpamKeywordLogAllByCreatedAtBefore(twoHoursAgo)
    }

    /**
     * 뉴스 제목에서 스팸 필터링에 사용할 키워드를 추출합니다.
     * HTML 태그, 특정 문자열(속보, 단독)을 제거하고 공백으로 분리합니다.
     *
     * @param title 키워드를 추출할 뉴스 제목
     * @return 추출된 키워드 리스트
     */
    private fun extractKeywords(title: String): List<String> {
        return title.split(" ")
            .map { it.replace(Regex(RegexConstants.NEWS_TYPE_TAG_REGEX), "").trim() }
            .filter { it.isNotBlank() }
    }
}
