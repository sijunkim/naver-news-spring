package com.news.naver.data.dto

/**
 * 네이버 뉴스 검색 API 요청 파라미터를 담는 데이터 클래스입니다.
 *
 * @property display 한 번에 표시할 검색 결과 개수 (기본값: 30, 최댓값: 100)
 * @property start 검색 시작 위치 (기본값: 1, 최댓값: 1000)
 * @property sort 정렬 옵션 (기본값: date, sim: 유사도순)
 */
data class Search(
    val display: Int = 30,
    val start: Int = 1,
    val sort: String = "date"
)
