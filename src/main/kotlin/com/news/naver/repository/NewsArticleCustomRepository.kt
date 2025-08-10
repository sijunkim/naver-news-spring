package com.news.naver.repository

import com.news.naver.entity.NewsArticleEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.flow.toList

@Repository
class NewsArticleCustomRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    suspend fun selectNewsArticleExistsByNaverLinkHash(hash: String): Boolean {
        val sql = "SELECT COUNT(*) FROM news_article WHERE naver_link_hash = :hash"
        return template.databaseClient.sql(sql)
            .bind("hash", hash)
            .map { row, _ -> row.get(0, java.lang.Long::class.java) }
            .awaitSingle() > 0
    }

    suspend fun selectNewsArticleTopByPublishedAtDesc(): NewsArticleEntity? {
        val sql = "SELECT * FROM news_article ORDER BY published_at DESC LIMIT 1"
        return template.databaseClient.sql(sql)
            .map { row, metaData -> converter.read(NewsArticleEntity::class.java, row, metaData) }
            .awaitSingleOrNull()
    }
}
