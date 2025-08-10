package com.news.naver.repository

import com.news.naver.entity.SpamKeywordLogEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.stereotype.Repository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import java.time.LocalDateTime

@Repository
class SpamKeywordLogCustomRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {

    suspend fun countSpamKeywordLogByKeywordIn(keywords: List<String>): Long {
        val sql = "SELECT COUNT(*) FROM spam_keyword_log WHERE keyword IN (:keywords)"
        return template.databaseClient.sql(sql)
            .bind("keywords", keywords)
            .map { row, _ -> row.get(0, java.lang.Long::class.java) }
            .awaitSingle()
    }

    suspend fun deleteSpamKeywordLogAllByCreatedAtBefore(dateTime: LocalDateTime) {
        val sql = "DELETE FROM spam_keyword_log WHERE created_at < :dateTime"
        template.databaseClient.sql(sql)
            .bind("dateTime", dateTime)
            .fetch()
            .rowsUpdated()
            .awaitSingleOrNull()
    }
}
