package com.news.naver.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * KST(한국 표준시, Asia/Seoul) 기준 날짜/시간 유틸리티
 *
 * 이 클래스의 모든 함수는 타임존과 무관하게 항상 KST 기준 시간을 반환합니다.
 * 시스템 타임존이나 JVM 설정에 영향받지 않으며, 데이터베이스와 동일한 타임존을 보장합니다.
 *
 * ## 사용 예시:
 * ```kotlin
 * val now = DateTimeUtils.now()                    // 현재 KST LocalDateTime
 * val today = DateTimeUtils.today()                // 현재 KST LocalDate
 * val midnight = DateTimeUtils.today().atStartOfDayKST()  // 오늘 자정 KST
 * val yesterday = DateTimeUtils.today().minusDays(1)
 * ```
 *
 * ## 주의사항:
 * - 절대 `LocalDateTime.now()` 사용 금지 (시스템 타임존 의존)
 * - 절대 `LocalDate.now()` 사용 금지 (시스템 타임존 의존)
 * - 반드시 `DateTimeUtils.now()`, `DateTimeUtils.today()` 사용
 */
object DateTimeUtils {
    /**
     * KST 타임존 (Asia/Seoul)
     */
    val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * 현재 KST 기준 LocalDateTime 반환
     *
     * @return 현재 한국 표준시 (타임존 정보 없음)
     */
    fun now(): LocalDateTime = LocalDateTime.now(KST)

    /**
     * 현재 KST 기준 LocalDate 반환
     *
     * @return 현재 한국 표준시 날짜
     */
    fun today(): LocalDate = LocalDate.now(KST)

    /**
     * 현재 KST 기준 ZonedDateTime 반환
     *
     * @return 현재 한국 표준시 (타임존 정보 포함)
     */
    fun nowZoned(): ZonedDateTime = ZonedDateTime.now(KST)
}

/**
 * LocalDate를 KST 자정(00:00:00) LocalDateTime으로 변환
 *
 * 이 확장 함수는 LocalDate.atStartOfDay()와 달리 시스템 타임존에 영향받지 않고
 * 항상 KST 기준 자정을 반환합니다.
 *
 * ## 사용 예시:
 * ```kotlin
 * val date = LocalDate.of(2026, 1, 10)
 * val midnight = date.atStartOfDayKST()  // 2026-01-10T00:00:00 KST
 * ```
 *
 * @receiver LocalDate 변환할 날짜
 * @return KST 기준 해당 날짜의 자정(00:00:00) LocalDateTime
 */
fun LocalDate.atStartOfDayKST(): LocalDateTime {
    return this.atStartOfDay(DateTimeUtils.KST).toLocalDateTime()
}
