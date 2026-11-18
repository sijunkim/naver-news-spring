package com.news.naver.util.processor

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * LLM 이 이미 판정한 제목의 결과(통과/제외)를 잠시 기억한다.
 *
 * 스팸으로 걸러져 DB 에 저장되지 않은 기사는 다음 폴링(1분 뒤)에도 "새 기사"로 다시 들어온다.
 * 기억하지 않으면 같은 제목을 매분 LLM 에 다시 물어 무료 한도(하루 토큰)를 소진한다
 * (2026-11-18 실측: 기사 처리 296건 중 고유 제목 174건, 같은 기사 최대 8분 연속).
 *
 * 프로세스 메모리에만 둔다 — 재시작하면 비워지고, 그때 한 번 다시 물으면 된다.
 *
 * @param ttl 판정을 기억하는 시간
 * @param maxSize 최대 항목 수. 넘으면 만료 항목을 먼저 지우고, 그래도 넘으면 전부 비운다.
 */
class LlmVerdictCache(
    private val ttl: Duration = Duration.ofHours(6),
    private val maxSize: Int = 5_000,
    private val clock: Clock = Clock.systemUTC()
) {
    private data class Entry(val keep: Boolean, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** 기억한 판정. 없거나 만료면 null. */
    fun get(title: String): Boolean? {
        val entry = entries[title] ?: return null
        if (!entry.expiresAt.isAfter(clock.instant())) {
            entries.remove(title, entry)
            return null
        }
        return entry.keep
    }

    fun put(title: String, keep: Boolean) {
        if (entries.size >= maxSize) {
            val now = clock.instant()
            entries.entries.removeIf { !it.value.expiresAt.isAfter(now) }
            if (entries.size >= maxSize) entries.clear()
        }
        entries[title] = Entry(keep, clock.instant().plus(ttl))
    }

    val size: Int get() = entries.size
}
