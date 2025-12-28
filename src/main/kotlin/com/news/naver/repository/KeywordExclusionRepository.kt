package com.news.naver.repository

import com.news.naver.data.enum.ExclusionScope
import com.news.naver.entity.KeywordExclusionEntity
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Repository

@Repository
class KeywordExclusionRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {
    suspend fun selectKeywordExclusionAllByScopeIn(scopes: List<ExclusionScope>): List<KeywordExclusionEntity> {
        val sql = "SELECT * FROM keyword_exclusion WHERE scope IN (:scopes)"
        return template.databaseClient.sql(sql)
            .bind("scopes", scopes.map { it.name })
            .map { row, meta -> converter.read(KeywordExclusionEntity::class.java, row, meta) }
            .all()
            .collectList()
            .awaitSingle()
    }
}