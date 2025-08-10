package com.news.naver.repository

import com.news.naver.domain.PressExclusion
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PressExclusionRepository : CoroutineCrudRepository<PressExclusion, Long>
