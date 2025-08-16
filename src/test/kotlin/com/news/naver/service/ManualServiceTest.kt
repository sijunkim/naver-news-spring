package com.news.naver.service

import com.news.naver.data.enum.NewsChannel
import com.news.naver.repository.RuntimeStateRepository
import com.news.naver.repository.SpamKeywordLogRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class ManualServiceTest {

    @InjectMocks
    private lateinit var manualService: ManualService

    @Mock
    private lateinit var newsProcessingService: NewsProcessingService

    @Mock
    private lateinit var spamKeywordLogRepository: SpamKeywordLogRepository

    @Mock
    private lateinit var runtimeStateRepository: RuntimeStateRepository

    @Test
    fun `속보 실행 시 NewsProcessingService의 runOnce가 호출된다`() = runTest {
        // when
        manualService.runBreakingNewsPoll()

        // then
        verify(newsProcessingService).runOnce(NewsChannel.BREAKING)
    }

    @Test
    fun `단독 실행 시 NewsProcessingService의 runOnce가 호출된다`() = runTest {
        // when
        manualService.runExclusiveNewsPoll()

        // then
        verify(newsProcessingService).runOnce(NewsChannel.EXCLUSIVE)
    }

    @Test
    fun `스팸 키워드 리셋 시 리포지토리의 deleteAll이 호출된다`() = runTest {
        // when
        manualService.resetSpamKeywords()

        // then
        verify(spamKeywordLogRepository).deleteAll()
    }

    @Test
    fun `폴링 타임스탬프 삭제 시 리포지토리의 deleteByKeyStartingWith가 호출된다`() = runTest {
        // when
        manualService.deletePollTimestamps()

        // then
        verify(runtimeStateRepository).deleteByKeyStartingWith("last_poll_time")
    }

    @Test
    fun `전체 런타임 데이터 리셋 시 키워드와 타임스탬프 삭제가 모두 호출된다`() = runTest {
        // when
        manualService.resetRuntimeData()

        // then
        verify(spamKeywordLogRepository).deleteAll()
        verify(runtimeStateRepository).deleteByKeyStartingWith("last_poll_time")
    }
}
