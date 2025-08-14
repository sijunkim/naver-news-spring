package com.news.naver.repository

import com.news.naver.entity.NewsArticleEntity
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class NewsArticleRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {
    suspend fun selectNewsArticleByHash(hash: String): NewsArticleEntity? {
        val sql = "SELECT * FROM news_article WHERE naver_link_hash = :hash LIMIT 1"
        return template.databaseClient.sql(sql)
            .bind("hash", hash)
            .map { row, meta -> converter.read(NewsArticleEntity::class.java, row, meta) }
            .one()
            .awaitSingleOrNull()
    }

    /**
     * MySQL R2DBC에서 RETURNING이 어려울 수 있어, 행수만 반환합니다.
     * 필요 시 직후 selectNewsArticleByHash(hash)로 id를 조회하세요.
     */
    suspend fun insertNewsArticle(
        naverLinkHash: String,
        title: String,
        summary: String?,
        companyId: Long?,
        publishedAt: LocalDateTime?,
        fetchedAt: LocalDateTime,
        rawJson: String?
    ): Long {
        val sql = """
            INSERT INTO news_article
              (naver_link_hash, title, summary, company_id, published_at, fetched_at, raw_json)
            VALUES
              (:hash, :title, :summary, :companyId, :publishedAt, :fetchedAt, :rawJson)
        """.trimIndent()

        return template.databaseClient.sql(sql)
            .bind("hash", naverLinkHash)
            .bind("title", title)
            .bind("summary", summary)
            .bind("companyId", companyId)
            .bind("publishedAt", publishedAt)
            .bind("fetchedAt", fetchedAt)
            .bind("rawJson", rawJson)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun countNewsArticleByHash(hash: String): Long {
        val sql = "SELECT COUNT(1) AS cnt FROM news_article WHERE naver_link_hash = :hash"
        return template.databaseClient.sql(sql)
            .bind("hash", hash)
            .map { row, _ -> (row.get("cnt") as Number).toLong() }
            .one()
            .awaitSingle()
    }
}