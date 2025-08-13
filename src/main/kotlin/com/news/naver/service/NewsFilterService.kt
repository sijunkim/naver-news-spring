package com.news.naver.service

import com.news.naver.data.enum.ExclusionScope
import com.news.naver.data.enum.NewsChannel
import com.news.naver.repository.KeywordExclusionRepository
import com.news.naver.repository.PressExclusionRepository
import org.springframework.stereotype.Service

@Service
class NewsFilterService(
    private val keywordRepo: KeywordExclusionRepository,
    private val pressRepo: PressExclusionRepository
) {

    /**
     * 제목/언론사 기반 제외 여부
     * - 제외 키워드: scope IN (ALL, {채널})
     * - 제외 언론사: press_exclusion 전체
     */
    suspend fun isExcluded(title: String?, companyDomain: String?, channel: NewsChannel): Boolean {
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
        val domain = companyDomain?.lowercase()
        if (domain != null && excludedPress.any { domain.contains(it) }) return true

        return false
    }
}