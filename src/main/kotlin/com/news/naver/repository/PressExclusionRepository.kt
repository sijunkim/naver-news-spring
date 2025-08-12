package com.news.naver.repository

import com.news.naver.entity.PressExclusionEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Criteria.where

@Repository
class PressExclusionRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    // CREATE
    suspend fun createPressExclusion(entity: PressExclusionEntity): PressExclusionEntity {
        return template.insert(entity).awaitSingle()
    }

    // SELECT by ID
    suspend fun selectPressExclusionById(id: Long): PressExclusionEntity? {
        return template.selectOne(Query.query(where("id").eq(id)), PressExclusionEntity::class.java).awaitSingleOrNull()
    }

    // SELECT ALL
    suspend fun selectPressExclusionAll(): List<PressExclusionEntity> {
        return template.select(PressExclusionEntity::class.java).all().collectList().awaitSingle()
    }

    // UPDATE
    suspend fun updatePressExclusion(entity: PressExclusionEntity): PressExclusionEntity {
        return template.update(entity).awaitSingle()
    }

    // DELETE by ID
    suspend fun deletePressExclusionById(id: Long) {
        template.delete(Query.query(where("id").eq(id)), PressExclusionEntity::class.java).awaitSingleOrNull()
    }
}
