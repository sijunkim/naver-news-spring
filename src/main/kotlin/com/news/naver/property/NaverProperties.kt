/**
 * 네이버 API 관련 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `naver` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property openapi Open API 관련 설정
 */
@ConfigurationProperties(prefix = "naver")
data class NaverProperties(
    val openapi: OpenApi
) {
    /**
     * 네이버 Open API의 상세 설정을 담는 데이터 클래스입니다.
     *
     * @property url API 엔드포인트 URL
     * @property clientId 클라이언트 ID
     * @property clientSecret 클라이언트 Secret
     */
    data class OpenApi(
        val url: String,
        val clientId: String,
        val clientSecret: String
    )
}
