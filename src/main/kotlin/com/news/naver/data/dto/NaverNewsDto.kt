/**
 * 네이버 뉴스 Open API의 XML 응답을 매핑하기 위한 데이터 클래스입니다.
 * `jackson-dataformat-xml` 라이브러리를 사용하여 XML 요소를 Kotlin 객체로 변환합니다.
 */
@JsonRootName("rss")
data class NaverNewsResponse(
    /**
     * RSS 피드의 채널 정보를 담고 있습니다.
     */
    @field:JacksonXmlProperty(localName = "channel")
    val channel: Channel
) {
    /**
     * RSS 채널의 상세 정보를 나타내는 데이터 클래스입니다.
     *
     * @property items 뉴스 기사 목록
     */
    data class Channel(
        @field:JacksonXmlProperty(localName = "item")
        val items: List<Item> = emptyList()
    )

    /**
     * 개별 뉴스 기사 아이템의 상세 정보를 나타내는 데이터 클래스입니다.
     *
     * @property title 기사 제목
     * @property originalLink 기사의 원본 링크
     * @property link 기사의 네이버 링크
     * @property description 기사 내용 요약
     * @property pubDate 기사 발행일시 (RFC 1123 형식)
     */
    data class Item(
        @field:JacksonXmlProperty(localName = "title")
        val title: String,
        @field:JacksonXmlProperty(localName = "originallink")
        val originalLink: String,
        @field:JacksonXmlProperty(localName = "link")
        val link: String,
        @field:JacksonXmlProperty(localName = "description")
        val description: String,
        @field:JacksonXmlProperty(localName = "pubDate")
        val pubDate: String
    )
}
