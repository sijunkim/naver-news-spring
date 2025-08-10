/**
 * 키워드 제외(KeywordExclusionEntity) 데이터에 접근하기 위한 리포지토리 인터페이스입니다.
 * Spring Data R2DBC의 CoroutineCrudRepository를 확장하여 비동기 DB 작업을 지원합니다.
 */
@Repository
interface KeywordExclusionRepository : CoroutineCrudRepository<KeywordExclusionEntity, Long> {
    /**
     * 주어진 스코프에 해당하는 모든 키워드 제외 목록을 Flow로 반환합니다.
     *
     * @param scope 제외 스코프 (BREAKING, EXCLUSIVE, ALL)
     * @return 해당 스코프의 키워드 제외 목록 Flow
     */
    fun findAllByScope(scope: ExclusionScope): Flow<KeywordExclusionEntity>

    /**
     * 주어진 여러 스코프에 해당하는 모든 키워드 제외 목록을 Flow로 반환합니다.
     *
     * @param scopes 제외 스코프 리스트
     * @return 해당 스코프들의 키워드 제외 목록 Flow
     */
    fun findAllByScopeIn(scopes: List<ExclusionScope>): Flow<KeywordExclusionEntity>
}
