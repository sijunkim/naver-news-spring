package com.news.naver.repository

import com.news.naver.entity.SpamKeywordLogEntity
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.awaitSingleOrNull
import org.springframework.stereotype.Repository

@Repository
class SpamKeywordLogRepository(
    private val template: R2dbcEntityTemplate,
) {

    suspend fun findFirstByKeywordAndCreatedAtAfter(keyword: String, createdAt: java.time.LocalDateTime): SpamKeywordLogEntity? {
        val sql = """
            SELECT id, keyword, count, created_at
            FROM spam_keyword_log
            WHERE keyword = :keyword
            AND created_at > :createdAt
        """.trimIndent()
        return template.databaseClient.sql(sql)
            .bind("keyword", keyword)
            .bind("createdAt", createdAt)
            .map { row, _ ->
                SpamKeywordLogEntity(
                    id = row.get("id", Long::class.java)!!,
                    keyword = row.get("keyword", String::class.java)!!,
                    count = row.get("count", Integer::class.java)!!.toInt(),
                    createdAt = row.get("created_at", java.time.LocalDateTime::class.java)!!
                )
            }
            .awaitSingleOrNull()
    }

    suspend fun upsert(keyword: String) {
        val sql = """
            INSERT INTO spam_keyword_log (keyword, count, created_at)
            VALUES (:keyword, 1, NOW())
            ON DUPLICATE KEY UPDATE count = count + 1
        """.trimIndent()

        template.databaseClient.sql(sql)
            .bind("keyword", keyword)
            .fetch()
            .rowsUpdated()
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
