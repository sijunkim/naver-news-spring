package com.news.naver.repository

import com.news.naver.entity.DeliveryLogEntity
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.bind
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class DeliveryLogRepository(
    private val template: R2dbcEntityTemplate,
    private val converter: MappingR2dbcConverter
) {
    suspend fun insertDeliveryLog(
        articleId: Long,
        channel: String,
        status: String,
        httpStatus: Int?,
        sentAt: LocalDateTime,
        responseBody: String?
    ): Long {
        val sql = """
            INSERT INTO delivery_log
              (article_id, channel, status, http_status, sent_at, response_body)
            VALUES
              (:articleId, :channel, :status, :httpStatus, :sentAt, :responseBody)
        """.trimIndent()

        return template.databaseClient.sql(sql)
            .bind("articleId", articleId)
            .bind("channel", channel)
            .bind("status", status)
            .bind("httpStatus", httpStatus)
            .bind("sentAt", sentAt)
            .bind("responseBody", responseBody)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun selectDeliveryLogByArticleIdAndChannel(articleId: Long, channel: String): DeliveryLogEntity? {
        val sql = """
            SELECT * FROM delivery_log
            WHERE article_id = :articleId AND channel = :channel
            LIMIT 1
        """.trimIndent()

        return template.databaseClient.sql(sql)
            .bind("articleId", articleId)
            .bind("channel", channel)
            .map { row, meta -> converter.read(DeliveryLogEntity::class.java, row, meta) }
            .one()
            .awaitSingleOrNull()
    }

    suspend fun countDeliveryLogByArticleIdAndChannel(articleId: Long, channel: String): Long {
        val sql = """
            SELECT COUNT(1) AS cnt
            FROM delivery_log
            WHERE article_id = :articleId AND channel = :channel
        """.trimIndent()

        return template.databaseClient.sql(sql)
            .bind("articleId", articleId)
            .bind("channel", channel)
            .map { row, _ -> (row.get("cnt") as Number).toLong() }
            .one()
            .awaitSingle()
    }

    suspend fun deleteAll() : Long {
        val sql = "DELETE FROM delivery_log"
        return template.databaseClient.sql(sql)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}