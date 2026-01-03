package com.news.naver.health

/**
 * 애플리케이션 시작 시 수행되는 공통 헬스체크 인터페이스입니다.
 * 각 저장소/외부 시스템에 대한 연결 검증 로직은 이 인터페이스를 구현해 추가합니다.
 */
interface HealthChecker {
    suspend fun check()
}
