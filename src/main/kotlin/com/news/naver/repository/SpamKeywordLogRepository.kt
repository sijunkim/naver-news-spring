package com.news.naver.repository

import com.news.naver.entity.SpamKeywordLogEntity
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SpamKeywordLogRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {
    suspend fun insertSpamKeywordLog(keyword: String, createdAt: LocalDateTime): Long {
        val sql = """
            INSERT INTO spam_keyword_log (keyword, created_at)
            VALUES (:keyword, :createdAt)
        """.trimIndent()
        return template.databaseClient.sql(sql)
            .bind("keyword", keyword)
            .bind("createdAt", createdAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun countSpamKeywordLogByKeywordSince(keyword: String, since: LocalDateTime): Long {
        val sql = """
            SELECT COUNT(1) AS cnt
            FROM spam_keyword_log
            WHERE keyword = :keyword
              AND created_at >= :since
        """.trimIndent()
        return template.databaseClient.sql(sql)
            .bind("keyword", keyword)
            .bind("since", since)
            .map { row, _ -> (row.get("cnt") as Number).toLong() }
            .one()
            .awaitSingle()
    }

    suspend fun deleteAll(): Long {
        val sql = "DELETE FROM spam_keyword_log"
        return template.databaseClient.sql(sql)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}