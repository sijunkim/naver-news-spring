package com.news.naver.health

import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redis 연결 상태를 확인하는 `HealthChecker` 구현입니다.
 * 간단한 GET 요청을 통해 기동 시 Redis 접근 가능 여부를 검증합니다.
 */
@Component
@Order(1)
class RedisHealthChecker(
    private val redisTemplate: ReactiveStringRedisTemplate
) : HealthChecker {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Redis 서버에 PING 명령을 보내 연결 상태만 확인합니다.
     * 응답이 없거나 에러가 발생하면 예외를 던져 기동을 중단시킬 수 있습니다.
     */
    override suspend fun check() {
        try {
            val result = redisTemplate.connectionFactory
                .reactiveConnection
                .ping()
                .awaitSingle()
                ?: throw IllegalStateException("Redis connection factory is not available")

            logger.info("✅ Redis PING successful, result: {}", result)
        } catch (e: Exception) {
            logger.error("❌ Redis connection failed", e)
            throw e
        }
    }
}
