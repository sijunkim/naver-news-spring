package com.news.naver.repository

import com.news.naver.entity.PressExclusionEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.flow.toList

@Repository
class PressExclusionCustomRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    suspend fun selectPressExclusionAll(): List<PressExclusionEntity> {
        val sql = "SELECT * FROM press_exclusion"
        return template.databaseClient.sql(sql)
            .map { row, metaData -> converter.read(PressExclusionEntity::class.java, row, metaData) }
            .all()
            .collectList()
            .awaitSingle()
    }
}
