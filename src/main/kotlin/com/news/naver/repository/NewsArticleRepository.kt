/**
 * 뉴스 기사(NewsArticleEntity) 데이터에 접근하기 위한 리포지토리 인터페이스입니다.
 * Spring Data R2DBC의 CoroutineCrudRepository를 확장하여 비동기 DB 작업을 지원합니다.
 */
@Repository
interface NewsArticleRepository : CoroutineCrudRepository<NewsArticleEntity, Long> {
    /**
     * 주어진 네이버 링크 해시(naverLinkHash)를 가진 뉴스 기사가 존재하는지 확인합니다.
     *
     * @param hash 확인할 네이버 링크 해시
     * @return 존재하면 true, 아니면 false
     */
    suspend fun existsByNaverLinkHash(hash: String): Boolean

    /**
     * 발행일(publishedAt)을 기준으로 가장 최신 뉴스 기사를 찾아 반환합니다.
     * R2DBC는 `findTopByOrderBy...`를 직접 지원하지 않으므로, 모든 데이터를 가져와 코틀린에서 처리합니다.
     * 대규모 데이터셋에서는 비효율적일 수 있으나, 현재 사용 사례에서는 허용됩니다.
     *
     * @return 가장 최신 뉴스 기사 엔티티 또는 없을 경우 null
     */
    suspend fun findTopByOrderByPublishedAtDesc(): NewsArticleEntity? {
        // R2DBC does not support findTop... directly, so we emulate it.
        // This is not efficient for large datasets, but acceptable for this use case.
        return findAll().toList().maxByOrNull { it.publishedAt!! }
    }
}
