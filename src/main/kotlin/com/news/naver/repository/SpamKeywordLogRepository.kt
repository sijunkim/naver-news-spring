package com.news.naver.repository

import com.news.naver.entity.SpamKeywordLogEntity
import io.r2dbc.spi.Row
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SpamKeywordLogRepository(
    private val template: R2dbcEntityTemplate,
) {

    suspend fun findByKeyword(keyword: String): SpamKeywordLogEntity? {
        val sql = """
            SELECT id, keyword, count, created_at
            FROM spam_keyword_log
            WHERE keyword = :keyword
        """.trimIndent()
        return template.databaseClient.sql(sql)
            .bind("keyword", keyword)
            .map(::mapRowToEntity)
            .one()
            .awaitSingleOrNull()
    }

    suspend fun save(entity: SpamKeywordLogEntity): SpamKeywordLogEntity {
        return if (entity.id == null) {
            insert(entity)
        } else {
            update(entity)
        }
    }

    private suspend fun insert(entity: SpamKeywordLogEntity): SpamKeywordLogEntity {
        val sql = """
            INSERT INTO spam_keyword_log (keyword, count, created_at)
            VALUES (:keyword, :count, :createdAt)
        """.trimIndent()

        val id = template.databaseClient.sql(sql)
            .bind("keyword", entity.keyword)
            .bind("count", entity.count)
            .bind("createdAt", entity.createdAt)
            .fetch()
            .first()
            .map { it["id"] as Long }
            .awaitSingle()

        return entity.copy(id = id)
    }

    private suspend fun update(entity: SpamKeywordLogEntity): SpamKeywordLogEntity {
        val sql = """
            UPDATE spam_keyword_log
            SET count = :count
            WHERE id = :id
        """.trimIndent()
        template.databaseClient.sql(sql)
            .bind("count", entity.count)
            .bind("id", entity.id!!)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return entity
    }

    suspend fun deleteAll(): Long {
        val sql = "DELETE FROM spam_keyword_log"
        return template.databaseClient.sql(sql)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun mapRowToEntity(row: Row): SpamKeywordLogEntity {
        return SpamKeywordLogEntity(
            id = row.get("id", Long::class.java)!!,
            keyword = row.get("keyword", String::class.java)!!,
            count = row.get("count", Integer::class.java)!!.toInt(),
            createdAt = row.get("created_at", LocalDateTime::class.java)!!
        )
    }
}
