package com.news.naver.repository

import com.news.naver.entity.NewsArticleEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Criteria.where

@Repository
class NewsArticleRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    // CREATE
    suspend fun createNewsArticle(entity: NewsArticleEntity): NewsArticleEntity {
        return template.insert(entity).awaitSingle()
    }

    // SELECT by ID
    suspend fun selectNewsArticleById(id: Long): NewsArticleEntity? {
        return template.selectOne(Query.query(where("id").eq(id)), NewsArticleEntity::class.java).awaitSingleOrNull()
    }

    // SELECT ALL
    suspend fun selectNewsArticleAll(): List<NewsArticleEntity> {
        return template.select(NewsArticleEntity::class.java).all().collectList().awaitSingle()
    }

    // UPDATE
    suspend fun updateNewsArticle(entity: NewsArticleEntity): NewsArticleEntity {
        return template.update(entity).awaitSingle()
    }

    // DELETE by ID
    suspend fun deleteNewsArticleById(id: Long) {
        template.delete(Query.query(where("id").eq(id)), NewsArticleEntity::class.java).awaitSingleOrNull()
    }

    // Custom Queries (from previous implementation)
    suspend fun countNewsArticleByNaverLinkHash(hash: String): Long {
        val sql = "SELECT COUNT(*) FROM news_article WHERE naver_link_hash = :hash"
        return template.databaseClient.sql(sql)
            .bind("hash", hash)
            .map { row, _ -> row.get(0, java.lang.Long::class.java) }
            .awaitSingle()
    }

    suspend fun selectNewsArticleLatest(): NewsArticleEntity? {
        val sql = "SELECT * FROM news_article ORDER BY published_at DESC LIMIT 1"
        return template.databaseClient.sql(sql)
            .map { row, metaData -> converter.read(NewsArticleEntity::class.java, row, metaData) }
            .awaitSingleOrNull()
    }
}
