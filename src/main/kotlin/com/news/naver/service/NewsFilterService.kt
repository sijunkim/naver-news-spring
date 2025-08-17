package com.news.naver.service

import com.news.naver.data.enum.ExclusionScope
import com.news.naver.data.enum.NewsChannel
import com.news.naver.property.AppProperties
import com.news.naver.repository.KeywordExclusionRepository
import com.news.naver.repository.PressExclusionRepository
import org.springframework.stereotype.Service

@Service
class NewsFilterService(
    private val keywordRepo: KeywordExclusionRepository,
    private val pressRepo: PressExclusionRepository,
    private val spamFilterService: NewsSpamFilterService,
    private val appProperties: AppProperties
) {

    /**
     * 기사를 처리해야 하는지(필터링 되지 않는지) 확인합니다.
     * 제외 키워드, 제외 언론사, 스팸 키워드를 모두 검사합니다.
     *
     * @return 처리해야 하면 true, 필터링 대상이면 false
     */
    suspend fun shouldProcess(title: String, companyName: String?, channel: NewsChannel): Boolean {
        // 기존 제외 로직
        val isExcluded = isExcluded(title, companyName, channel)
        if (isExcluded) return false

        // 스팸 검사 로직
        val isSpam = spamFilterService.isSpamByTitleTokens(title, threshold = appProperties.duplicate.threshold)
        if (isSpam) return false

        return true
    }

    private suspend fun isExcluded(title: String?, companyName: String?, channel: NewsChannel): Boolean {
        val scopes = listOf(ExclusionScope.ALL, channel.toExclusionScope())
        val keywords = keywordRepo
            .selectKeywordExclusionAllByScopeIn(scopes)
            .map { it.keyword.lowercase() }
            .toSet()

        val titleLower = title?.lowercase().orEmpty().replace(" ", "")
        if (keywords.any { it.isNotBlank() && titleLower.contains(it) }) return true

        val excludedPress = pressRepo.selectPressExclusionAll()
            .map { it.pressName.lowercase() }
            .toSet()
        val name = companyName?.lowercase()
        if (name != null && excludedPress.contains(name)) return true

        return false
    }
}