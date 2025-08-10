/**
 * 뉴스 수집, 필터링, 저장 및 슬랙 전송의 전체 파이프라인을 처리하는 서비스 클래스입니다.
 * NestJS 프로젝트의 핵심 비즈니스 로직을 Kotlin 코루틴 기반으로 재구현했습니다.
 *
 * @property naverNewsClient 네이버 뉴스 API 호출 클라이언트
 * @property slackClient 슬랙 웹훅 호출 클라이언트
 * @property newsFilterService 뉴스 필터링 로직을 담당하는 서비스
 * @property newsRefinerService 뉴스 데이터 정제 및 슬랙 페이로드 생성을 담당하는 서비스
 * @property newsSpamFilterService 스팸 키워드 필터링 로직을 담당하는 서비스
 * @property newsArticleRepository 뉴스 기사 데이터에 접근하기 위한 리포지토리
 * @property deliveryLogRepository 전송 로그 데이터에 접근하기 위한 리포지토리
 * @property slackProperties 슬랙 관련 설정
 */
@Service
class NewsProcessingService(
    private val naverNewsClient: NaverNewsClient,
    private val slackClient: SlackClient,
    private val newsFilterService: NewsFilterService,
    private val newsRefinerService: NewsRefinerService,
    private val newsSpamFilterService: NewsSpamFilterService,
    private val newsArticleRepository: NewsArticleRepository,
    private val deliveryLogRepository: DeliveryLogRepository,
    private val slackProperties: SlackProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 특정 뉴스 채널(속보, 단독 등)에 대한 뉴스 처리 파이프라인을 실행합니다.
     * 다음 단계를 포함합니다:
     * 1. 마지막 수신 시간보다 새로운 뉴스 필터링
     * 2. DB에 없는 새로운 뉴스 필터링 (해시 기준)
     * 3. 스팸 키워드 필터링
     * 4. 제외 키워드/언론사 필터링
     * 5. 필터링을 통과한 뉴스 저장 및 슬랙 전송
     *
     * @param channel 처리할 뉴스 채널 (BREAKING, EXCLUSIVE, DEV)
     */
    suspend fun processNews(channel: com.news.naver.data.enum.NewsChannel) = coroutineScope {
        logger.info("Start processing news for channel: ${channel.name}")

        val lastArticleTime = newsArticleRepository.findTopByOrderByPublishedAtDesc()?.publishedAt ?: LocalDateTime.now().minusDays(1)
        val excludedKeywords = newsFilterService.getExcludedKeywords(channel)
        val excludedPresses = newsFilterService.getExcludedPresses()

        val newsItems = naverNewsClient.fetchNews(channel.query).channel.items

        newsItems.asFlow()
            .map { item -> Triple(item, parseDate(item.pubDate), normalizeUrl(item.originalLink)) }
            .filter { (_, pubDate, _) -> pubDate.isAfter(lastArticleTime) } // 1. 마지막 수신 시간보다 새로운 뉴스만
            .map { (item, pubDate, normalizedUrl) ->
                val hash = HashUtils.sha256(normalizedUrl)
                quadruple(item, pubDate, normalizedUrl, hash)
            }
            .filter { (_, _, _, hash) -> !newsArticleRepository.existsByNaverLinkHash(hash) } // 2. DB에 없는 새로운 뉴스만 (해시 기준)
            .filter { (item, _, _, _) -> !newsSpamFilterService.isSpam(item.title) } // 3. 스팸 키워드 필터링
            .filter { (item, _, _, _) -> !newsFilterService.filter(item, excludedKeywords, excludedPresses) } // 4. 제외 키워드/언론사 필터링
            .map { (item, pubDate, normalizedUrl, hash) -> createNewsArticle(item, pubDate, normalizedUrl, hash) }
            .toList()
            .forEach { article ->
                launch {
                    val savedArticle = newsArticleRepository.save(article)
                    logger.info("New article saved: ${savedArticle.title}")

                    newsSpamFilterService.recordKeywords(savedArticle.title) // 스팸 필터링을 위해 키워드 기록

                    val payload = newsRefinerService.createSlackPayload(savedArticle, channel)
                    val webhookUrl = getWebhookUrl(channel)
                    slackClient.sendMessage(webhookUrl, payload)

                    deliveryLogRepository.save(
                        DeliveryLogEntity(
                            articleId = savedArticle.id!!,
                            channel = channel,
                            status = DeliveryStatus.SUCCESS,
                            httpStatus = 200,
                            responseBody = "OK"
                        )
                    )
                }
            }
        logger.info("Finished processing news for channel: ${channel.name}")
    }

    /**
     * 주어진 URL을 정규화합니다. 프로토콜, 호스트, 경로만 남기고 쿼리 파라미터나 프래그먼트를 제거합니다.
     *
     * @param url 정규화할 원본 URL
     * @return 정규화된 URL 문자열
     */
    private fun normalizeUrl(url: String): String {
        val parsedUrl = URL(url)
        return "${parsedUrl.protocol}://${parsedUrl.host}${parsedUrl.path}"
    }

    /**
     * 네이버 뉴스 API 응답 아이템을 NewsArticleEntity로 변환합니다.
     * 이 과정에서 제목, 요약, 언론사 정보 등을 정제합니다.
     *
     * @param item 네이버 뉴스 API 응답의 개별 아이템
     * @param pubDate 파싱된 발행일시 (LocalDateTime)
     * @param normalizedUrl 정규화된 기사 URL
     * @param hash 정규화된 URL의 SHA-256 해시
     * @return 변환된 NewsArticleEntity 객체
     */
    private suspend fun createNewsArticle(item: NaverNewsResponse.Item, pubDate: LocalDateTime, normalizedUrl: String, hash: String): NewsArticleEntity {
        return NewsArticleEntity(
            naverLinkHash = hash,
            title = newsRefinerService.refineHtml(item.title),
            summary = newsRefinerService.refineHtml(item.description),
            press = newsRefinerService.extractPress(item.originalLink),
            publishedAt = pubDate,
            rawJson = item.toString()
        )
    }

    /**
     * 주어진 발행일 문자열을 LocalDateTime 객체로 파싱합니다.
     * RFC_1123_DATE_TIME 형식을 사용합니다.
     *
     * @param pubDate 파싱할 발행일 문자열
     * @return 파싱된 LocalDateTime 객체
     */
    private fun parseDate(pubDate: String): LocalDateTime {
        return try {
            ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime()
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    }

    /**
     * 뉴스 채널에 해당하는 슬랙 웹훅 URL을 반환합니다.
     *
     * @param channel 뉴스 채널
     * @return 해당 채널의 슬랙 웹훅 URL
     */
    private fun getWebhookUrl(channel: com.news.naver.data.enum.NewsChannel): String {
        return when (channel) {
            com.news.naver.data.enum.NewsChannel.BREAKING -> slackProperties.webhook.breaking
            com.news.naver.data.enum.NewsChannel.EXCLUSIVE -> slackProperties.webhook.exclusive
            com.news.naver.data.enum.NewsChannel.DEV -> slackProperties.webhook.develop
        }
    }
}

/**
 * 네 개의 값을 묶는 제네릭 데이터 클래스입니다.
 * Kotlin 1.3 이상에서는 Triple을 확장하여 사용할 수 있습니다.
 */
fun <A, B, C, D> quadruple(a: A, b: B, c: C, d: D): Quadruple<A, B, C, D> = Quadruple(a, b, c, d)
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * NewsArticleRepository에 추가되어야 할 확장 함수입니다.
 * 발행일(publishedAt)을 기준으로 가장 최신 뉴스 기사를 찾아 반환합니다.
 * R2DBC는 `findTopByOrderBy...`를 직접 지원하지 않으므로, 모든 데이터를 가져와 코틀린에서 처리합니다.
 * 대규모 데이터셋에서는 비효율적일 수 있으나, 현재 사용 사례에서는 허용됩니다.
 *
 * @return 가장 최신 뉴스 기사 엔티티 또는 없을 경우 null
 */
suspend fun com.news.naver.repository.NewsArticleRepository.findTopByOrderByPublishedAtDesc(): com.news.naver.entity.NewsArticleEntity? = this.findAll().toList().maxByOrOrNull { it.publishedAt!! }
