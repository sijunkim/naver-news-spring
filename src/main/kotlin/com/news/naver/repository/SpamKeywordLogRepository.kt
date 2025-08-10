/**
 * 스팸 키워드 로그(SpamKeywordLogEntity) 데이터에 접근하기 위한 리포지토리 인터페이스입니다.
 * Spring Data R2DBC의 CoroutineCrudRepository를 확장하여 비동기 DB 작업을 지원합니다.
 */
@Repository
interface SpamKeywordLogRepository : CoroutineCrudRepository<SpamKeywordLogEntity, Long> {

    /**
     * 주어진 키워드 목록에 해당하는 스팸 키워드 로그의 개수를 반환합니다.
     *
     * @param keywords 확인할 키워드 목록
     * @return 해당 키워드들의 스팸 로그 개수
     */
    @Query("SELECT COUNT(*) FROM spam_keyword_log WHERE keyword IN (:keywords)")
    suspend fun countByKeywordIn(keywords: List<String>): Long

    /**
     * 특정 시간 이전에 생성된 모든 스팸 키워드 로그를 삭제합니다.
     *
     * @param dateTime 삭제 기준 시간
     */
    suspend fun deleteAllByCreatedAtBefore(dateTime: LocalDateTime)
}
