package com.news.naver.repository

import com.news.naver.domain.DeliveryLog
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface DeliveryLogRepository : CoroutineCrudRepository<DeliveryLog, Long>
