package com.news.naver.repository

import com.news.naver.data.enum.ExclusionScope
import com.news.naver.entity.KeywordExclusionEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.flow.toList

@Repository
class KeywordExclusionCustomRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    suspend fun selectKeywordExclusionAllByScope(scope: ExclusionScope): List<KeywordExclusionEntity> {
        val sql = "SELECT * FROM keyword_exclusion WHERE scope = :scope"
        return template.databaseClient.sql(sql)
            .bind("scope", scope.name)
            .map { row, metaData -> converter.read(KeywordExclusionEntity::class.java, row, metaData) }
            .all()
            .collectList()
            .awaitSingle()
    }

    suspend fun selectKeywordExclusionAllByScopeIn(scopes: List<ExclusionScope>): List<KeywordExclusionEntity> {
        val sql = "SELECT * FROM keyword_exclusion WHERE scope IN (:scopes)"
        return template.databaseClient.sql(sql)
            .bind("scopes", scopes.map { it.name })
            .map { row, metaData -> converter.read(KeywordExclusionEntity::class.java, row, metaData) }
            .all()
            .collectList()
            .awaitSingle()
    }
}
