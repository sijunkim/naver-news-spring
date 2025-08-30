package com.news.naver.service

import com.news.naver.entity.NewsCompanyEntity
import com.news.naver.repository.NewsCompanyRepository
import org.springframework.stereotype.Service

@Service
class NewsCompanyService(
    private val newsCompanyRepository: NewsCompanyRepository,
    private val metadataFetcher: WebsiteMetadataFetcher
) {

    suspend fun findOrCreateCompany(domain: String): NewsCompanyEntity {
        // 1. 웹사이트에서 언론사 이름을 가져옵니다. 실패하면 도메인을 이름으로 사용합니다.
        val name = metadataFetcher.fetchPageTitle(domain) ?: domain

        // 2. 레포지토리의 원자적 "조회 또는 생성" 메소드를 호출합니다.
        return newsCompanyRepository.findOrCreateByDomainPrefix(domain, name)
    }
}
