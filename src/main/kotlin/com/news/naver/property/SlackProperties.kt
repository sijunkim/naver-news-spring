/**
 * Slack 관련 설정을 담는 ConfigurationProperties 클래스입니다.
 * `application.yml` 또는 `.env` 파일의 `slack` 접두사로 시작하는 속성들을 매핑합니다.
 *
 * @property webhook 웹훅 관련 설정
 */
@ConfigurationProperties(prefix = "slack")
data class SlackProperties(
    val webhook: Webhook
) {
    /**
     * Slack 웹훅 URL 설정을 담는 데이터 클래스입니다.
     *
     * @property breaking 속보 채널 웹훅 URL
     * @property exclusive 단독 채널 웹훅 URL
     * @property develop 개발/테스트 채널 웹훅 URL
     */
    data class Webhook(
        val breaking: String,
        val exclusive: String,
        val develop: String
    )
}
