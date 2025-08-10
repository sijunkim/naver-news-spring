/**
 * Slack Webhook과 통신하는 클라이언트 클래스입니다.
 * `WebClient`를 사용하여 비동기적으로 메시지를 슬랙으로 전송합니다.
 * 전송 실패 시 재시도 로직을 포함합니다.
 */
@Component
class SlackClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val webClient: WebClient

    init {
        val httpClient = HttpClient.create()
            .doOnConnected {
                it.addHandlerLast(ReadTimeoutHandler(10, TimeUnit.SECONDS))
                it.addHandlerLast(WriteTimeoutHandler(10, TimeUnit.SECONDS))
            }

        webClient = WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    /**
     * 주어진 웹훅 URL로 슬랙 메시지를 전송합니다.
     * 전송 실패 시 3회 재시도(지수 백오프) 로직이 적용됩니다.
     *
     * @param webhookUrl 메시지를 전송할 슬랙 웹훅 URL
     * @param payload 전송할 메시지 페이로드 (Map 형태, Slack Block Kit 포함 가능)
     */
    suspend fun sendMessage(webhookUrl: String, payload: Map<String, Any>) {
        webClient.post()
            .uri(webhookUrl)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono<String>()
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).jitter(0.75))
            .doOnError { e -> logger.error("Slack 전송 실패: $payload", e) }
            .onErrorResume { Mono.empty() } // 에러 발생 시에도 흐름이 끊기지 않도록 함
            .awaitSingle() // 코루틴 컨텍스트에서 실행
    }
}
