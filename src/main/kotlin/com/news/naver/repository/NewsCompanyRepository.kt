package com.news.naver.repository

import com.news.naver.entity.NewsCompanyEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Criteria.where

@Repository
class NewsCompanyRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    // CREATE
    suspend fun createNewsCompany(entity: NewsCompanyEntity): NewsCompanyEntity {
        return template.insert(entity).awaitSingle()
    }

    // SELECT by ID
    suspend fun selectNewsCompanyById(id: Long): NewsCompanyEntity? {
        return template.selectOne(Query.query(where("id").eq(id)), NewsCompanyEntity::class.java).awaitSingleOrNull()
    }

    // SELECT ALL
    suspend fun selectNewsCompanyAll(): List<NewsCompanyEntity> {
        return template.select(NewsCompanyEntity::class.java).all().collectList().awaitSingle()
    }

    // UPDATE
    suspend fun updateNewsCompany(entity: NewsCompanyEntity): NewsCompanyEntity {
        return template.update(entity).awaitSingle()
    }

    // DELETE by ID
    suspend fun deleteNewsCompanyById(id: Long) {
        template.delete(Query.query(where("id").eq(id)), NewsCompanyEntity::class.java).awaitSingleOrNull()
    }
}
