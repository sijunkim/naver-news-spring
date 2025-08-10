/**
 * 네이버 뉴스 Open API와 통신하는 클라이언트 클래스입니다.
 * `WebClient`를 사용하여 비동기적으로 뉴스 데이터를 가져옵니다.
 *
 * @property naverProperties 네이버 API 관련 설정 (URL, Client ID, Client Secret)
 */
@Component
class NaverNewsClient(private val naverProperties: NaverProperties) {

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(naverProperties.openapi.url)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
        .defaultHeader("X-Naver-Client-Id", naverProperties.openapi.clientId)
        .defaultHeader("X-Naver-Client-Secret", naverProperties.openapi.clientSecret)
        .build()

    /**
     * 주어진 쿼리(예: "속보", "단독")로 네이버 뉴스 API를 호출하여 뉴스 데이터를 가져옵니다.
     *
     * @param query 검색할 뉴스 쿼리 문자열
     * @return 네이버 뉴스 API 응답을 나타내는 `NaverNewsResponse` 객체
     */
    suspend fun fetchNews(query: String): NaverNewsResponse {
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder.queryParam("query", query).build()
            }
            .retrieve()
            .awaitBody()
    }
}
