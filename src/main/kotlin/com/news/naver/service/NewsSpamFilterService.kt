package com.news.naver.service

import com.news.naver.property.AppProperties
import com.news.naver.repository.SpamKeywordLogRepository
import com.news.naver.util.DateTimeUtils
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 뉴스 제목 기반 스팸 필터링 서비스입니다.
 *
 * ## 동작 원리
 * 1. 뉴스 제목을 토큰(단어)으로 분해
 * 2. 각 토큰이 2시간 내에 몇 번 등장했는지 카운트
 * 3. 임계값 이상 등장한 토큰이 많으면 스팸으로 판정
 *
 * ## Redis 사용 이유
 * - **속도**: 초당 수천 건의 뉴스를 빠르게 처리
 * - **TTL**: 2시간 후 자동 삭제로 메모리 관리
 * - **원자적 연산**: increment 명령으로 동시성 문제 해결
 *
 * ## Fallback 메커니즘
 * - Redis 장애 시 자동으로 Database로 전환
 * - `redisAvailable` 플래그로 상태 추적
 * - 주기적으로 Redis 복구 시도
 *
 * @property redisTemplate Redis 연결 템플릿 (카운터 저장용)
 * @property spamRepo Database 백업 저장소
 * @property appProperties 스팸 판정 임계값 설정
 */
@Service
class NewsSpamFilterService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val spamRepo: SpamKeywordLogRepository,
    private val appProperties: AppProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 스팸 감지 시간 윈도우 (Redis TTL 및 Database 조회 기준) */
        private val SPAM_DETECTION_WINDOW = Duration.ofHours(2)

        /** 스팸 필터링에서 제외할 키워드 (속보, 단독 등 일반적으로 자주 사용되는 단어) */
        private val EXCLUDED_KEYWORDS = setOf("속보", "단독")
    }

    /** Redis Value 연산 헬퍼 */
    private val valueOps = redisTemplate.opsForValue()

    /** Redis 키 접두사: "news:spam:title:{token}" 형태로 저장 */
    private val spamKeyPrefix = "news:spam:title:"

    /** Redis 연결 상태 확인용 더미 키 */
    private val healthCheckKey = "${spamKeyPrefix}__health__"

    /** 토큰 카운트 유효 기간 (2시간) */
    private val windowTtl = SPAM_DETECTION_WINDOW

    /** Redis 사용 가능 여부 플래그 (장애 시 Database로 fallback) */
    private val redisAvailable = AtomicBoolean(true)

    /**
     * 뉴스 제목이 스팸인지 판단합니다.
     *
     * ## 동작 과정
     * 1. 제목을 토큰으로 분해 (예: "대통령 발표" → ["대통령", "발표"], 속보/단독 제외)
     * 2. 각 토큰을 Redis/DB에서 조회하여 2시간 내 등장 횟수 확인
     * 3. 등장 횟수를 1 증가시킴
     * 4. **이전에 등장한 적이 있는 토큰**의 개수를 카운트
     * 5. 카운트가 임계값(기본 3) 이상이면 스팸으로 판정
     *
     * ## 예시
     * ```
     * 임계값 = 3
     *
     * 뉴스 1: "속보 대통령 발표" → ["대통령", "발표"] 처음 등장 → 스팸 아님
     * 뉴스 2: "속보 대통령 국회" → ["대통령", "국회"] "대통령" 재등장(1) → 스팸 아님 (1 < 3)
     * 뉴스 3: "단독 대통령 주가" → ["대통령", "주가"] "대통령" 재등장(2) → 스팸 아님 (2 < 3)
     * 뉴스 4: "속보 대통령 날씨" → ["대통령", "날씨"] "대통령" 재등장(3) → 스팸! (3 >= 3)
     *
     * 참고: "속보", "단독"은 토큰에서 제외되므로 카운트되지 않음
     * ```
     *
     * ## Redis vs Database
     * - **정상**: Redis에서 빠르게 처리 (1ms 이내)
     * - **Redis 장애**: Database로 자동 전환 (10-50ms)
     *
     * ## 제외 키워드
     * - "속보", "단독"은 토큰화 단계에서 제외됨
     * - 따라서 이들 키워드는 스팸 판정에 영향 없음
     *
     * @param title 뉴스 제목
     * @return true면 스팸, false면 정상
     */
    suspend fun isSpamByTitleTokens(title: String): Boolean {
        val tokens = tokenize(title)
        if (tokens.isEmpty()) return false

        val threshold = appProperties.duplicate.threshold
        val windowStart = DateTimeUtils.now().minus(SPAM_DETECTION_WINDOW)
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

    /**
     * 모든 스팸 키워드 카운터를 초기화합니다.
     *
     * ## 동작 과정
     * 1. Redis에서 "news:spam:title:*" 패턴의 모든 키를 SCAN으로 조회
     * 2. 조회된 키들을 일괄 삭제
     * 3. Database의 spam_keyword_log 테이블도 전체 삭제
     *
     * ## Redis SCAN 명령
     * - KEYS 대신 SCAN 사용 (운영 환경 안전)
     * - KEYS는 전체 키를 한 번에 조회해서 Redis 블로킹 발생
     * - SCAN은 커서 기반으로 나눠서 조회 (블로킹 없음)
     *
     * ## 사용 시기
     * - 테스트 환경 초기화
     * - 스팸 필터 로직 변경 후 재시작
     * - 장기간 누적된 데이터 정리
     *
     * @return 삭제된 총 키/레코드 개수 (Redis + Database)
     */
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

    /**
     * Redis 연결 복구를 시도합니다.
     *
     * ## 동작 방식
     * 1. Health check 키를 GET으로 조회 (더미 값, 존재 여부 무관)
     * 2. 조회 성공 시 Redis가 복구된 것으로 판단
     * 3. `redisAvailable` 플래그를 true로 변경
     *
     * ## 호출 시점
     * - `isSpamByTitleTokens`에서 Redis 사용 불가 상태일 때마다 시도
     * - 실패해도 조용히 반환 (로그 없음)
     *
     * @see redisAvailable
     */
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

    /**
     * Redis를 사용 불가 상태로 표시합니다.
     *
     * ## 동작 방식
     * 1. `redisAvailable` 플래그를 false로 변경
     * 2. 최초 1회만 경고 로그 출력 (중복 로그 방지)
     * 3. 이후 요청은 자동으로 Database fallback 사용
     *
     * ## AtomicBoolean.compareAndSet
     * - `compareAndSet(true, false)`: 현재 값이 true면 false로 변경하고 true 반환
     * - 동시에 여러 스레드가 호출해도 **딱 1번만** 로그 출력
     *
     * @param ex Redis 작업 실패 예외
     */
    private fun markRedisUnavailable(ex: Exception) {
        if (redisAvailable.compareAndSet(true, false)) {
            logger.warn("Redis increment failed. Falling back to database. status=redis_down", ex)
        }
    }

    /**
     * 토큰을 Redis 키로 변환합니다.
     *
     * @param token 토큰 문자열
     * @return Redis 키 (예: "news:spam:title:속보")
     */
    private fun spamKey(token: String): String = "$spamKeyPrefix$token"

    /**
     * Redis를 사용하여 토큰 카운트를 증가시킵니다.
     *
     * ## 동작 과정 (원자적 연산으로 개선됨)
     * 1. Redis INCR: 카운트를 1 증가 (키가 없으면 0→1로 생성)
     * 2. 반환값이 1이면 처음 생성된 키 → TTL 설정
     * 3. Database UPSERT: 백업 레코드 생성/갱신
     * 4. 반환값 > 1이면 true (이전에 등장한 토큰)
     *
     * ## 개선 사항
     * - **GET 제거**: INCR 반환값으로 판단 (1회 호출 → 원자적)
     * - **TTL 확인 제거**: newValue == 1로 처음 생성 여부 판단
     * - **Race condition 방지**: INCR만 사용하여 동시성 문제 해결
     *
     * ## INCR 동작
     * ```
     * INCR "news:spam:title:속보"
     * → 키 없음: 0→1 반환 (처음 등장)
     * → 키 있음(1): 1→2 반환 (2번째 등장)
     * → 키 있음(2): 2→3 반환 (3번째 등장)
     * ```
     *
     * ## TTL (Time To Live)
     * - Redis 키는 2시간 후 자동 삭제
     * - 메모리 누수 방지
     * - 최초 생성 시(newValue==1)에만 TTL 설정
     *
     * @param token 토큰 문자열
     * @return true면 이전에 등장한 토큰 (newValue > 1), false면 처음 등장 (newValue == 1)
     * @throws Exception Redis 연결 실패 시
     */
    private suspend fun incrementViaRedis(token: String): Boolean {
        val key = spamKey(token)

        // INCR: 키가 없으면 0에서 시작하여 1로 증가, 있으면 +1
        val newValue = valueOps.increment(key).awaitSingle()

        // 처음 생성된 키(값=1)면 TTL 설정
        if (newValue == 1L) {
            redisTemplate.expire(key, windowTtl).awaitSingleOrNull()
        }

        // Database 백업
        spamRepo.upsert(token)

        // 2 이상이면 이전에 등장한 토큰
        return newValue > 1
    }

    /**
     * Database를 사용하여 토큰 카운트를 증가시킵니다.
     *
     * ## 동작 과정
     * 1. 2시간 이내에 등장한 토큰 레코드 조회
     * 2. UPSERT: 레코드 생성 또는 갱신 (created_at 업데이트)
     * 3. 이전 레코드가 있으면 true 반환
     *
     * ## Redis vs Database
     * | 항목 | Redis | Database |
     * |------|-------|----------|
     * | 속도 | 1ms | 10-50ms |
     * | 영속성 | 2시간 TTL | 영구 저장 |
     * | 복구 | 재시작 시 손실 | 재시작 후에도 유지 |
     *
     * @param token 토큰 문자열
     * @param windowStart 2시간 전 시각 (이후 레코드만 조회)
     * @return true면 이전에 등장한 토큰, false면 처음 등장
     */
    private suspend fun incrementViaDatabase(token: String, windowStart: LocalDateTime): Boolean {
        val entity = spamRepo.findFirstByKeywordAndCreatedAtAfter(token, windowStart)
        spamRepo.upsert(token)
        return entity != null
    }

    /**
     * 뉴스 제목을 토큰(단어) 리스트로 분해합니다.
     *
     * ## 처리 과정
     * 1. 소문자 변환: "속보 대통령" → "속보 대통령"
     * 2. 특수문자 제거: "속보! 대통령" → "속보 대통령"
     * 3. 공백으로 분리: ["속보", "대통령"]
     * 4. 길이 2 미만 필터: "속보 및 발표" → ["속보", "대통령", "발표"] (및 제외)
     * 5. 제외 키워드 필터: "속보 대통령 발표" → ["대통령", "발표"] (속보 제외)
     * 6. 중복 제거: "대통령 대통령" → ["대통령"]
     *
     * ## 제외 키워드
     * - "속보", "단독": 뉴스 채널명으로 자주 사용되어 스팸 판정에 부적합
     * - 향후 추가 필요 시 `EXCLUDED_KEYWORDS`에 추가
     *
     * ## 정규식 설명
     * - `\p{L}`: 모든 언어의 문자 (한글, 영어, 일본어 등)
     * - `\p{Nd}`: 숫자 (0-9)
     * - `[^\p{L}\p{Nd}]+`: 문자/숫자가 아닌 것들 (특수문자, 공백 등)
     *
     * ## 예시
     * ```
     * "속보! 대통령 발표" → ["대통령", "발표"]  (속보 제외)
     * "단독 Breaking News: 주가 급등" → ["breaking", "news", "주가", "급등"]  (단독 제외)
     * "1월 2일 날씨" → ["1월", "2일", "날씨"]
     * ```
     *
     * @param title 뉴스 제목
     * @return 정규화된 토큰 리스트 (중복 없음, 길이 2 이상, 제외 키워드 필터링됨)
     */
    fun tokenizeTitle(title: String): List<String> =
        title
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in EXCLUDED_KEYWORDS }
            .distinct()

    /**
     * 내부용 토큰화 함수 (tokenizeTitle의 별칭)
     */
    private fun tokenize(title: String): List<String> = tokenizeTitle(title)
}
