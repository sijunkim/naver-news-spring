/**
 * 뉴스 필터링 로직을 담당하는 서비스 클래스입니다.
 * 제외 키워드 및 언론사 목록을 관리하고, 이를 기반으로 뉴스를 필터링합니다.
 *
 * @property keywordExclusionRepository 키워드 제외 목록 데이터에 접근하기 위한 리포지토리
 * @property pressExclusionRepository 언론사 제외 목록 데이터에 접근하기 위한 리포지토리
 */
@Service
class NewsFilterService(
    private val keywordExclusionRepository: KeywordExclusionRepository,
    private val pressExclusionRepository: PressExclusionRepository
) {

    /**
     * 주어진 뉴스 채널에 해당하는 제외 키워드 목록을 가져옵니다.
     * `ALL` 스코프와 해당 채널 스코프에 해당하는 키워드를 모두 포함합니다.
     *
     * @param channel 뉴스 채널 (BREAKING, EXCLUSIVE, DEV)
     * @return 제외할 키워드들의 Set (소문자 처리됨)
     */
    suspend fun getExcludedKeywords(channel: com.news.naver.data.enum.NewsChannel): Set<String> {
        val scopes = listOf(com.news.naver.data.enum.ExclusionScope.ALL, com.news.naver.data.enum.ExclusionScope.valueOf(channel.name))
        return keywordExclusionRepository.findAllByScopeIn(scopes)
            .map { it.keyword.lowercase() }
            .toList()
            .toSet()
    }

    /**
     * 제외할 언론사 목록을 가져옵니다.
     *
     * @return 제외할 언론사 이름들의 Set (소문자 처리됨)
     */
    suspend fun getExcludedPresses(): Set<String> {
        return pressExclusionRepository.findAll()
            .map { it.pressName.lowercase() }
            .toList()
            .toSet()
    }

    /**
     * 주어진 뉴스 아이템이 필터링 규칙에 의해 제외되어야 하는지 확인합니다.
     * 제목에 제외 키워드나 제외 언론사가 포함되어 있는지 검사합니다.
     *
     * @param item 네이버 뉴스 API 응답의 개별 아이템
     * @param excludedKeywords 제외할 키워드 Set
     * @param excludedPresses 제외할 언론사 Set
     * @return 뉴스가 제외되어야 하면 true, 아니면 false
     */
    fun filter(item: com.news.naver.data.dto.NaverNewsResponse.Item, excludedKeywords: Set<String>, excludedPresses: Set<String>): Boolean {
        val title = item.title.lowercase()
        // 언론사 정보는 API 응답에 없으므로, 제목에 포함된 경우를 가정하여 필터링 (한계점)
        if (excludedPresses.any { title.contains(it) }) {
            return true
        }
        if (excludedKeywords.any { title.contains(it) }) {
            return true
        }
        return false
    }
}
