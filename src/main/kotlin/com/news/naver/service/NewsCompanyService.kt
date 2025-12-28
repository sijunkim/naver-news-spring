package com.news.naver.service

import com.news.naver.entity.NewsCompanyEntity
import com.news.naver.repository.NewsCompanyRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NewsCompanyService(
    private val newsCompanyRepository: NewsCompanyRepository,
    private val metadataFetcher: com.news.naver.util.fetcher.WebsiteMetadataFetcher
) {

    @Transactional
    suspend fun findOrCreateCompany(domain: String): NewsCompanyEntity {
        // 1. 데이터베이스에서 먼저 조회합니다.
        newsCompanyRepository.findByDomainPrefix(domain)?.let { return it }

        // 2. 데이터베이스에 없으면 웹사이트에서 언론사 이름을 가져옵니다.
        val name = metadataFetcher.fetchPageTitle(domain) ?: domain
        val newCompany = NewsCompanyEntity(domainPrefix = domain, name = name)

        // 3. 새로운 언론사를 저장합니다.
        return try {
            newsCompanyRepository.save(newCompany)
        } catch (e: DataIntegrityViolationException) {
            // 동시성 문제로 다른 스레드가 이미 저장한 경우, 다시 조회하여 반환합니다.
            newsCompanyRepository.findByDomainPrefix(domain)
                ?: throw IllegalStateException("Failed to find company by domain $domain after insert attempt.")
        }
    }
}
