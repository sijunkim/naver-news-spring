package com.news.naver.repository

import com.news.naver.data.enum.ExclusionScope
import com.news.naver.entity.KeywordExclusionEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.flow.toList
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Criteria.where

@Repository
class KeywordExclusionRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    // CREATE
    suspend fun createKeywordExclusion(entity: KeywordExclusionEntity): KeywordExclusionEntity {
        return template.insert(entity).awaitSingle()
    }

    // SELECT by ID
    suspend fun selectKeywordExclusionById(id: Long): KeywordExclusionEntity? {
        return template.selectOne(Query.query(where("id").eq(id)), KeywordExclusionEntity::class.java).awaitSingleOrNull()
    }

    // SELECT ALL
    suspend fun selectKeywordExclusionAll(): List<KeywordExclusionEntity> {
        return template.select(KeywordExclusionEntity::class.java).all().collectList().awaitSingle()
    }

    // UPDATE
    suspend fun updateKeywordExclusion(entity: KeywordExclusionEntity): KeywordExclusionEntity {
        return template.update(entity).awaitSingle()
    }

    // DELETE by ID
    suspend fun deleteKeywordExclusionById(id: Long) {
        template.delete(Query.query(where("id").eq(id)), KeywordExclusionEntity::class.java).awaitSingleOrNull()
    }

    // Custom Queries
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
