package com.news.naver.repository

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository

@Repository
class RuntimeStateRepository(
    private val template: R2dbcEntityTemplate
) {
    suspend fun getState(key: String): String? {
        val sql = "SELECT `value` FROM runtime_state WHERE `key` = :key"
        return template.databaseClient.sql(sql)
            .bind("key", key)
            .map { row -> row.get("value", String::class.java) }
            .one()
            .awaitSingleOrNull()
    }

    suspend fun setState(key: String, value: String) {
        val sql = """
            INSERT INTO runtime_state (`key`, `value`)
            VALUES (:key, :value)
            ON DUPLICATE KEY UPDATE `value` = :value
        """.trimIndent()
        template.databaseClient.sql(sql)
            .bind("key", key)
            .bind("value", value)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun deleteByKeyStartingWith(prefix: String): Long {
        val sql = "DELETE FROM runtime_state WHERE `key` LIKE :prefix"
        return template.databaseClient.sql(sql)
            .bind("prefix", "$prefix%")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
