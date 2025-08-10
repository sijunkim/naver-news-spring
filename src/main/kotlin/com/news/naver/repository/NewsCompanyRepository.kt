package com.news.naver.repository

import com.news.naver.domain.NewsCompany
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface NewsCompanyRepository : CoroutineCrudRepository<NewsCompany, Long>
