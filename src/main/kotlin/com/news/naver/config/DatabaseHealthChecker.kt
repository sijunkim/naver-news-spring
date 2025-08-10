/**
 * 데이터베이스 연결 상태를 확인하는 컴포넌트 클래스입니다.
 * 애플리케이션 시작 시 DB 연결이 정상적인지 검증하는 역할을 합니다.
 *
 * @property template R2DBC 데이터베이스 작업을 위한 템플릿
 */
@Component
class DatabaseHealthChecker(private val template: R2dbcEntityTemplate) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 데이터베이스 연결을 비동기적으로 확인합니다.
     * 간단한 `SELECT 1` 쿼리를 실행하여 연결 유효성을 검증합니다.
     * 연결 실패 시 에러를 로깅하고 예외를 다시 던져 애플리케이션 시작을 중단시킬 수 있습니다.
     */
    suspend fun check() {
        try {
            val result = template.databaseClient.sql("SELECT 1").map { row -> row.get(0, Integer::class.java) }.awaitOne()
            logger.info("✅ Database connection successful, result: $result")
        } catch (e: Exception) {
            logger.error("❌ Database connection failed", e)
            // 실패 시 애플리케이션을 종료하거나, 재시도 로직을 추가할 수 있습니다.
            throw e // 예외를 다시 던져서 애플리케이션 시작을 중단시킬 수 있습니다.
        }
    }
}
