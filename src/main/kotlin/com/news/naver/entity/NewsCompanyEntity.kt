/**
 * 언론사 정보를 나타내는 엔티티 클래스입니다.
 * `news_company` 테이블과 매핑됩니다.
 *
 * @property id 엔티티의 고유 식별자 (자동 증가)
 * @property domainPrefix 언론사 웹사이트의 도메인 접두사 (예: `chosun.com`)
 * @property name 언론사의 공식 이름 (예: `조선일보`)
 */
@Table("news_company")
data class NewsCompanyEntity(
    @Id
    val id: Long? = null,
    val domainPrefix: String,
    val name: String
)
