/**
 * 주기적으로 뉴스 수집 및 처리를 트리거하는 스케줄러 클래스입니다.
 * Spring의 `@Scheduled` 어노테이션을 사용하여 정해진 시간마다 작업을 실행합니다.
 *
 * @property processingService 뉴스 처리 파이프라인을 담당하는 서비스
 * @property spamFilterService 스팸 키워드 필터링 및 관리를 담당하는 서비스
 * @property appProperties 애플리케이션 관련 설정 (폴링 주기 등)
 */
@Component
class NewsPollingScheduler(
    private val processingService: NewsProcessingService,
    private val spamFilterService: NewsSpamFilterService,
    private val appProperties: AppProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 속보 뉴스를 매분(또는 설정된 주기)마다 폴링하여 처리합니다.
     * `app.poll.interval-seconds` 설정에 따라 주기적으로 실행됩니다.
     */
    @Scheduled(fixedRateString = "${app.poll.interval-seconds}000", initialDelay = 5000)
    fun pollBreakingNews() {
        runBlocking {
            launch {
                try {
                    processingService.processNews(NewsChannel.BREAKING)
                } catch (e: Exception) {
                    logger.error("Error during polling breaking news", e)
                }
            }
        }
    }

    /**
     * 단독 뉴스를 매분(또는 설정된 주기)마다 폴링하여 처리합니다.
     * `app.poll.interval-seconds` 설정에 따라 주기적으로 실행됩니다.
     */
    @Scheduled(fixedRateString = "${app.poll.interval-seconds}000", initialDelay = 10000)
    fun pollExclusiveNews() {
        runBlocking {
            launch {
                try {
                    processingService.processNews(NewsChannel.EXCLUSIVE)
                } catch (e: Exception) {
                    logger.error("Error during polling exclusive news", e)
                }
            }
        }
    }

    /**
     * 2시간마다 오래된 스팸 키워드 로그를 정리합니다.
     * NestJS의 `makeKeywordFilesCron` 로직을 DB 기반으로 재구현한 것입니다.
     */
    @Scheduled(cron = "0 0 0/2 * * *") // Every 2 hours
    fun cleanupOldSpamKeywords() {
        runBlocking {
            launch {
                try {
                    logger.info("Running scheduled cleanup of old spam keywords.")
                    spamFilterService.cleanupOldKeywords()
                } catch (e: Exception) {
                    logger.error("Error during spam keyword cleanup", e)
                } 
            }
        }
    }
}
