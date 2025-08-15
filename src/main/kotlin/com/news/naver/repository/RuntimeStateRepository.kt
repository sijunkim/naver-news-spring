package com.news.naver.repository

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository

@Repository
class RuntimeStateRepository(
    private val template: R2dbcEntityTemplate
) {
    suspend fun deleteByKeyStartingWith(prefix: String): Long {
        val sql = "DELETE FROM runtime_state WHERE `key` LIKE :prefix"
        return template.databaseClient.sql(sql)
            .bind("prefix", "$prefix%")
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
