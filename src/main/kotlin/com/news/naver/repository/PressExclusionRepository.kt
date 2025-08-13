package com.news.naver.repository

import com.news.naver.entity.PressExclusionEntity
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Repository

@Repository
class PressExclusionRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {
    suspend fun selectPressExclusionAll(): List<PressExclusionEntity> {
        val sql = "SELECT * FROM press_exclusion"
        return template.databaseClient.sql(sql)
            .map { row, meta -> converter.read(PressExclusionEntity::class.java, row, meta) }
            .all()
            .collectList()
            .awaitSingle()
    }
}