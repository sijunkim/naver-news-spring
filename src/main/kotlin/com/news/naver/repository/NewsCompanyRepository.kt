package com.news.naver.repository

import com.news.naver.entity.NewsCompanyEntity
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Repository

@Repository
class NewsCompanyRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {
    suspend fun selectNewsCompanyAll(): List<NewsCompanyEntity> {
        val sql = "SELECT * FROM news_company"
        return template.databaseClient.sql(sql)
            .map { row, meta -> converter.read(NewsCompanyEntity::class.java, row, meta) }
            .all()
            .collectList()
            .awaitSingle()
    }
}