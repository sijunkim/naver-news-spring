package com.news.naver.service

import com.news.naver.data.enum.ExclusionScope
import com.news.naver.data.enum.NewsChannel
import com.news.naver.repository.KeywordExclusionRepository
import com.news.naver.repository.PressExclusionRepository
import org.springframework.stereotype.Service

@Service
class NewsFilterService(
    private val keywordRepo: KeywordExclusionRepository,
    private val pressRepo: PressExclusionRepository,
) {

    suspend fun isExcluded(title: String?, companyName: String?, channel: NewsChannel): Boolean {
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

        return name != null && excludedPress.contains(name)
    }
}