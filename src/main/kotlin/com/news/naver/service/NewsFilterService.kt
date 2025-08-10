package com.news.naver.service

import com.news.naver.client.NaverNewsResponse
import com.news.naver.domain.ExclusionScope
import com.news.naver.domain.NewsChannel
import com.news.naver.repository.KeywordExclusionRepository
import com.news.naver.repository.PressExclusionRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
class NewsFilterService(
    private val keywordExclusionRepository: KeywordExclusionRepository,
    private val pressExclusionRepository: PressExclusionRepository
) {

    suspend fun getExcludedKeywords(channel: NewsChannel): Set<String> {
        val scopes = listOf(ExclusionScope.ALL, ExclusionScope.valueOf(channel.name))
        return keywordExclusionRepository.findAllByScopeIn(scopes)
            .map { it.keyword.lowercase() }
            .toList()
            .toSet()
    }

    suspend fun getExcludedPresses(): Set<String> {
        return pressExclusionRepository.findAll()
            .map { it.pressName.lowercase() }
            .toList()
            .toSet()
    }

    fun filter(item: NaverNewsResponse.Item, excludedKeywords: Set<String>, excludedPresses: Set<String>): Boolean {
        val title = item.title.lowercase()
        // 언론사 정보는 API 응답에 없으므로, 제목에 포함된 경우를 가정하여 필터링 (한계점)
        if (excludedPresses.any { title.contains(it) }) {
            return true
        }
        if (excludedKeywords.any { title.contains(it) }) {
            return true
        }
        return false
    }
}
