package com.news.naver.util.processor

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LlmVerdictCacheTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MutableClock(Instant.parse("2026-11-18T00:00:00Z"))

    @Test
    fun `기억한 판정을 돌려준다 - 통과와 제외 모두`() {
        val cache = LlmVerdictCache(Duration.ofHours(6), 10, clock)
        cache.put("일반 뉴스", true)
        cache.put("연예 뉴스", false)

        assertEquals(true, cache.get("일반 뉴스"))
        assertEquals(false, cache.get("연예 뉴스"))
        assertNull(cache.get("처음 보는 제목"))
    }

    @Test
    fun `ttl 이 지나면 잊는다`() {
        val cache = LlmVerdictCache(Duration.ofHours(6), 10, clock)
        cache.put("제목", true)

        clock.now = clock.now.plus(Duration.ofHours(6)).minusSeconds(1)
        assertEquals(true, cache.get("제목"))

        clock.now = clock.now.plusSeconds(1)
        assertNull(cache.get("제목"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `최대 크기에 닿으면 만료 항목부터 지우고 그래도 차 있으면 비운다`() {
        val cache = LlmVerdictCache(Duration.ofHours(1), 3, clock)
        cache.put("a", true)
        cache.put("b", true)
        clock.now = clock.now.plus(Duration.ofHours(2)) // a, b 만료
        cache.put("c", true)
        cache.put("d", true) // size 3 도달 → 만료된 a, b 정리 후 추가
        assertEquals(2, cache.size)

        cache.put("e", true) // size 3
        cache.put("f", true) // 만료 항목 없음 → 전부 비운 뒤 f 만 남음
        assertEquals(1, cache.size)
        assertEquals(true, cache.get("f"))
    }
}
