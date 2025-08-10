/**
 * 네이버 뉴스 수집 및 슬랙 전송 애플리케이션의 메인 진입점입니다.
 * Spring Boot 애플리케이션을 초기화하고, 필요한 설정을 활성화합니다.
 *
 * `@SpringBootApplication`: Spring Boot 애플리케이션임을 선언합니다.
 * `@EnableConfigurationProperties`: `application.yml` 또는 `.env` 파일의 설정 값을 타입-세이프하게 주입받을 수 있도록 합니다.
 * `@EnableScheduling`: `@Scheduled` 어노테이션을 사용하여 스케줄링 기능을 활성화합니다.
 */
@SpringBootApplication
@EnableConfigurationProperties(
    AppProperties::class,
    NaverProperties::class,
    SlackProperties::class
)
@EnableScheduling
class NewsApplication {

    /**
     * 애플리케이션 시작 시 데이터베이스 연결 상태를 확인하는 빈을 정의합니다.
     * `DatabaseHealthChecker`를 사용하여 실제 DB 연결을 검증합니다.
     *
     * @param healthChecker 데이터베이스 헬스 체크를 담당하는 서비스
     * @return 애플리케이션 시작 시 실행될 `ApplicationRunner` 인스턴스
     */
    @Bean
    fun databaseHealthCheck(healthChecker: DatabaseHealthChecker) = ApplicationRunner {
        runBlocking {
            healthChecker.check()
        }
    }
}

/**
 * Spring Boot 애플리케이션의 메인 함수입니다.
 * 애플리케이션을 실행합니다.
 *
 * @param args 커맨드 라인 인자
 */
fun main(args: Array<String>) {
    runApplication<NewsApplication>(*args)
}
