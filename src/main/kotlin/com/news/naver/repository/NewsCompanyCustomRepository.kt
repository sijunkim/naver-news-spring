package com.news.naver.repository

import com.news.naver.entity.NewsCompanyEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.flow.toList

@Repository
class NewsCompanyCustomRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    suspend fun selectNewsCompanyAll(): List<NewsCompanyEntity> {
        val sql = "SELECT * FROM news_company"
        return template.databaseClient.sql(sql)
            .map { row, metaData -> converter.read(NewsCompanyEntity::class.java, row, metaData) }
            .all()
            .collectList()
            .awaitSingle()
    }
}
