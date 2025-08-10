package com.news.naver.repository

import com.news.naver.entity.SpamKeywordLogEntity
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

/**
 * 스팸 키워드 로그(SpamKeywordLogEntity) 데이터에 접근하기 위한 리포지토리 인터페이스입니다.
 * Spring Data R2DBC의 CoroutineCrudRepository를 확장하여 비동기 DB 작업을 지원합니다.
 */
@Repository
interface SpamKeywordLogRepository : CoroutineCrudRepository<SpamKeywordLogEntity, Long> {
}
