package com.news.naver.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("news_company")
data class NewsCompany(
    @Id
    val id: Long? = null,
    val domainPrefix: String,
    val name: String
)
