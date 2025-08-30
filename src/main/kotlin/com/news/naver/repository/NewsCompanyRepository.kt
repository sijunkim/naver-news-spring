package com.news.naver.repository

import com.news.naver.entity.NewsCompanyEntity
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono

@Repository
class NewsCompanyRepository(
    private val template: R2dbcEntityTemplate,
    private val operator: TransactionalOperator
) {

    suspend fun findOrCreateByDomainPrefix(domain: String, name: String): NewsCompanyEntity {
        val insertSql = "INSERT IGNORE INTO news_company (domain_prefix, name) VALUES (:domain, :name)"
        val selectSql = "SELECT * FROM news_company WHERE domain_prefix = :domain LIMIT 1"

        val transactionalResult: Mono<NewsCompanyEntity> = operator.execute { tx ->
            template.databaseClient.sql(insertSql)
                .bind("domain", domain)
                .bind("name", name)
                .fetch()
                .rowsUpdated()
                .then(
                    template.databaseClient.sql(selectSql)
                        .bind("domain", domain)
                        .map { row, meta -> template.converter.read(NewsCompanyEntity::class.java, row, meta) }
                        .one()
                )
        }.single()

        return transactionalResult.awaitSingle()
    }
}