package com.news.naver.repository

import com.news.naver.domain.ExclusionScope
import com.news.naver.domain.KeywordExclusion
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface KeywordExclusionRepository : CoroutineCrudRepository<KeywordExclusion, Long> {
    fun findAllByScope(scope: ExclusionScope): Flow<KeywordExclusion>
    fun findAllByScopeIn(scopes: List<ExclusionScope>): Flow<KeywordExclusion>
}
