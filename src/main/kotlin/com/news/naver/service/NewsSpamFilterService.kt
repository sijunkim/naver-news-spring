package com.news.naver.service

import com.news.naver.property.AppProperties
import com.news.naver.repository.SpamKeywordLogRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

@Service
class NewsSpamFilterService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val spamRepo: SpamKeywordLogRepository,
    private val appProperties: AppProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val valueOps = redisTemplate.opsForValue()
    private val spamKeyPrefix = "news:spam:title:"
    private val healthCheckKey = "${spamKeyPrefix}__health__"
    private val windowTtl = Duration.ofHours(3)
    private val redisAvailable = AtomicBoolean(true)

    /**
     * 제목을 정규화된 토큰으로 분해한 뒤, Redis 카운터를 갱신하며 스팸 여부를 판단합니다.
     *  1) 토큰 이전 카운트가 threshold 이상이면 스팸 처리
     *  2) 이전에 등장한 토큰 개수가 threshold 이상이면 스팸 처리
     * 정책: 길이 2 미만 토큰은 무시, 동일 기사 내 중복 토큰은 1회만 집계
     */
    suspend fun isSpamByTitleTokens(title: String): Boolean {
        val tokens = tokenize(title)
        if (tokens.isEmpty()) return false

        val threshold = appProperties.duplicate.threshold
        val windowStart = LocalDateTime.now().minusHours(3)
        var matchedKeywordCount = 0

        if (!redisAvailable.get()) {
            tryRecoverRedis()
        }

        for (token in tokens) {
            val existedBefore = if (redisAvailable.get()) {
                try {
                    incrementViaRedis(token)
                } catch (ex: Exception) {
                    markRedisUnavailable(ex)
                    incrementViaDatabase(token, windowStart)
                }
            } else {
                incrementViaDatabase(token, windowStart)
            }

            if (existedBefore) {
                matchedKeywordCount++
                if (matchedKeywordCount >= threshold) {
                    return true
                }
            }
        }
        return false
    }

    suspend fun resetKeywordCounters(): Long {
        val redisDeleted = try {
            val keys = redisTemplate.scan(
                ScanOptions.scanOptions().match("$spamKeyPrefix*").build()
            ).collectList().awaitSingle()

            if (keys.isEmpty()) {
                0L
            } else {
                redisTemplate.delete(*keys.toTypedArray()).awaitSingle()
            }
        } catch (ex: Exception) {
            logger.warn("Redis reset failed. Proceeding with database reset only.", ex)
            0L
        }

        val dbDeleted = spamRepo.deleteAll()
        return redisDeleted + dbDeleted
    }

    private suspend fun tryRecoverRedis() {
        try {
            valueOps.get(healthCheckKey).awaitSingleOrNull()
            if (redisAvailable.compareAndSet(false, true)) {
                logger.info("Redis connectivity recovered. status=redis_recovered")
            }
        } catch (ex: Exception) {
            // 여전히 장애 상태면 조용히 반환
        }
    }

    private fun markRedisUnavailable(ex: Exception) {
        if (redisAvailable.compareAndSet(true, false)) {
            logger.warn("Redis increment failed. Falling back to database. status=redis_down", ex)
        }
    }

    private fun spamKey(token: String): String = "$spamKeyPrefix$token"

    private suspend fun incrementViaRedis(token: String): Boolean {
        val key = spamKey(token)
        val currentValue = valueOps.get(key).awaitSingleOrNull()?.toLongOrNull() ?: 0L
        val ttlBefore = redisTemplate.getExpire(key).awaitSingleOrNull()
        valueOps.increment(key).awaitSingle()
        val shouldSetTtl = ttlBefore == null || ttlBefore.isZero || ttlBefore.isNegative
        if (shouldSetTtl) {
            redisTemplate.expire(key, windowTtl).awaitSingleOrNull()
        }
        spamRepo.upsert(token)
        return currentValue > 0
    }

    private suspend fun incrementViaDatabase(token: String, windowStart: LocalDateTime): Boolean {
        val entity = spamRepo.findFirstByKeywordAndCreatedAtAfter(token, windowStart)
        spamRepo.upsert(token)
        return entity != null
    }

    fun tokenizeTitle(title: String): List<String> =
        title
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

    private fun tokenize(title: String): List<String> = tokenizeTitle(title)
}
