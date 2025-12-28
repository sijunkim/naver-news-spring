package com.news.naver.repository

import com.news.naver.entity.NewsCompanyEntity
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Repository

@Repository
class NewsCompanyRepository(
    private val template: R2dbcEntityTemplate
) {

    suspend fun findByDomainPrefix(domain: String): NewsCompanyEntity? {
        val selectSql = "SELECT * FROM news_company WHERE domain_prefix = :domain LIMIT 1"
        return template.databaseClient.sql(selectSql)
            .bind("domain", domain)
            .map { row, meta -> template.converter.read(NewsCompanyEntity::class.java, row, meta) }
            .one()
            .awaitSingleOrNull()
    }

    suspend fun save(entity: NewsCompanyEntity): NewsCompanyEntity {
        return template.insert(entity).awaitSingle()
    }
}
