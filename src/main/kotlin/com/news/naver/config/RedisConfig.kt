package com.news.naver.config

import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration

@Configuration
class RedisConfig {

    @Bean
    @Primary
    fun reactiveRedisConnectionFactory(
        redisProperties: RedisProperties
    ): ReactiveRedisConnectionFactory {
        val username = redisProperties.username
        val password = redisProperties.password
        if (logger.isInfoEnabled) {
            logger.info(
                "Initializing RedisConnectionFactory with username='${username ?: "default"}', passwordSet=${!password.isNullOrEmpty()}"
            )
        }
        val standalone = RedisStandaloneConfiguration().apply {
            hostName = redisProperties.host
            port = redisProperties.port
            database = redisProperties.database
            if (!username.isNullOrBlank()) {
                this.username = username
            }
            if (password != null && password.isNotEmpty()) {
                setPassword(RedisPassword.of(password))
            }
        }

        val clientBuilder = LettuceClientConfiguration.builder()
        val timeout = redisProperties.timeout
        if (timeout != null && timeout != Duration.ZERO) {
            clientBuilder.commandTimeout(timeout)
        }

        return LettuceConnectionFactory(standalone, clientBuilder.build())
    }

    @Bean
    fun reactiveStringRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory
    ): ReactiveStringRedisTemplate {
        return ReactiveStringRedisTemplate(connectionFactory)
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(RedisConfig::class.java)
    }
}
