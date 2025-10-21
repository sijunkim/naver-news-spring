package com.news.naver.config

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Redis 연결 상태를 확인하는 `HealthChecker` 구현입니다.
 * 간단한 GET 요청을 통해 기동 시 Redis 접근 가능 여부를 검증합니다.
 */
@Component
class RedisHealthChecker(
    private val redisTemplate: ReactiveStringRedisTemplate
) : HealthChecker {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val healthKey = "health:redis:check"

    /**
     * Redis에서 테스트 키를 조회하여 연결 성공 여부를 로그로 남깁니다.
     * 실패 시 예외를 던져 애플리케이션 기동을 중단시킬 수 있습니다.
     */
    override suspend fun check() {
        try {
            val value = redisTemplate.opsForValue()
                .get(healthKey)
                .awaitSingleOrNull()

            logger.info("✅ Redis connection successful, result: {}", value ?: "null")
        } catch (e: Exception) {
            logger.error("❌ Redis connection failed", e)
            throw e
        }
    }
}
