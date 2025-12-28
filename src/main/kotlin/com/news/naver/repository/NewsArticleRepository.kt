package com.news.naver.repository

import com.news.naver.data.dto.summary.DailyNewsItem
import com.news.naver.entity.NewsArticleEntity
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.delete
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
        link: String,
        originalLink: String?,
        title: String,
        summary: String?,
        companyId: Long?,
        publishedAt: LocalDateTime?,
        fetchedAt: LocalDateTime,
        rawJson: String?
    ): Long {
        val sql = """
            INSERT INTO news_article
              (naver_link_hash, naver_link, original_link, title, summary, company_id, published_at, fetched_at, raw_json)
            VALUES
              (:hash, :link, :originalLink, :title, :summary, :companyId, :publishedAt, :fetchedAt, :rawJson)
        """.trimIndent()

        return template.databaseClient.sql(sql)
            .bind("hash", naverLinkHash)
            .bind("link", link)
            .bind("originalLink", originalLink)
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

    suspend fun deleteAll(): Long {
        return template.delete(NewsArticleEntity::class.java).all().awaitSingle()
    }

    /**
     * 특정 기간 동안 발송된 뉴스를 조회합니다 (일일 요약용)
     *
     * @param startDateTime 시작 시간 (포함)
     * @param endDateTime 종료 시간 (미포함)
     * @return 발송된 뉴스 목록 (중복 제거됨)
     */
    suspend fun selectDeliveredNewsInDateRange(
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime
    ): List<DailyNewsItem> {
        val sql = """
            SELECT
                na.id AS article_id,
                na.title,
                na.summary,
                MIN(dl.sent_at) AS first_sent_at,
                GROUP_CONCAT(DISTINCT dl.channel ORDER BY dl.channel SEPARATOR ',') AS channels
            FROM delivery_log dl
            INNER JOIN news_article na ON dl.article_id = na.id
            WHERE dl.status = 'SUCCESS'
              AND dl.sent_at >= :startDateTime
              AND dl.sent_at < :endDateTime
            GROUP BY na.id, na.title, na.summary
            ORDER BY first_sent_at DESC
        """.trimIndent()

        return template.databaseClient.sql(sql)
            .bind("startDateTime", startDateTime)
            .bind("endDateTime", endDateTime)
            .map { row, _ ->
                DailyNewsItem(
                    articleId = (row.get("article_id") as Number).toLong(),
                    title = row.get("title") as String,
                    summary = row.get("summary") as String?,
                    firstSentAt = row.get("first_sent_at") as LocalDateTime,
                    channels = row.get("channels") as String
                )
            }
            .all()
            .collectList()
            .awaitSingle()
    }
}