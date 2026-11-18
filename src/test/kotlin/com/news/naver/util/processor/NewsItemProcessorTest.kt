package com.news.naver.util.processor

import com.news.naver.data.dto.Item
import com.news.naver.service.LLMService
import com.news.naver.service.LLMService.TitleFilterResult
import com.news.naver.util.refiner.NewsRefinerService
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewsItemProcessorTest {

    private val llmService = mock<LLMService>()
    private val processor = NewsItemProcessor(mock(), llmService, NewsRefinerService(), mock())

    private fun item(title: String, link: String) = Item(title, "https://press.example/$title", link, null, null)

    private val politics = item("정치 뉴스", "https://n.news.naver.com/mnews/article/001/0000000001")
    private val economy = item("경제 뉴스", "https://n.news.naver.com/mnews/article/001/0000000002")
    private val entertainByLink = item("연예 기사", "https://m.entertain.naver.com/article/108/0003386000")
    private val entertainByLlm = item("배우 근황", "https://n.news.naver.com/mnews/article/001/0000000003")

    /** LLM 이 kept 에 든 제목만 남겼다고 응답하도록 한다. */
    private suspend fun llmKeeps(vararg kept: String, judged: Boolean = true) {
        whenever(llmService.filterNewsTitles(any())).doReturn(TitleFilterResult(kept.toList(), judged))
    }

    private suspend fun askedTitles(): List<String> {
        val captor = argumentCaptor<List<String>>()
        verify(llmService).filterNewsTitles(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `연예 섹션 링크 판별`() {
        assertTrue(processor.isEntertainmentLink("https://m.entertain.naver.com/article/108/1"))
        assertTrue(processor.isEntertainmentLink("https://entertain.naver.com/read?oid=1"))
        assertFalse(processor.isEntertainmentLink("https://n.news.naver.com/mnews/article/001/1"))
        assertFalse(processor.isEntertainmentLink("https://m.sports.naver.com/kbaseball/article/1"))
        assertFalse(processor.isEntertainmentLink("https://enter.etoday.co.kr/news/1")) // 언론사 자체 연예 도메인은 LLM 몫
        assertFalse(processor.isEntertainmentLink(null))
        assertFalse(processor.isEntertainmentLink("잘못된 링크 %%"))
    }

    @Test
    fun `연예 섹션 링크는 LLM 에 묻지 않고 제외한다`() = runTest {
        llmKeeps("정치 뉴스")

        val result = processor.filterByLLM(listOf(politics, entertainByLink))

        assertEquals(listOf(politics), result)
        assertEquals(listOf("정치 뉴스"), askedTitles())
    }

    @Test
    fun `모두 연예 섹션 링크면 LLM 을 호출하지 않는다`() = runTest {
        val result = processor.filterByLLM(listOf(entertainByLink))

        assertEquals(emptyList(), result)
        verify(llmService, never()).filterNewsTitles(any())
    }

    @Test
    fun `한 번 판정한 제목은 다시 묻지 않는다 - 통과와 제외 모두 기억`() = runTest {
        llmKeeps("정치 뉴스")
        assertEquals(listOf(politics), processor.filterByLLM(listOf(politics, entertainByLlm)))

        // 다음 폴링: 같은 두 기사가 다시 들어와도 LLM 호출 없이 같은 결과
        assertEquals(listOf(politics), processor.filterByLLM(listOf(politics, entertainByLlm)))
        verify(llmService, times(1)).filterNewsTitles(any())
    }

    @Test
    fun `기억한 제목과 새 제목이 섞이면 새 제목만 묻는다`() = runTest {
        llmKeeps("정치 뉴스")
        processor.filterByLLM(listOf(politics))

        llmKeeps("경제 뉴스")
        val result = processor.filterByLLM(listOf(politics, economy))

        assertEquals(listOf(politics, economy), result)
        val captor = argumentCaptor<List<String>>()
        verify(llmService, times(2)).filterNewsTitles(captor.capture())
        assertEquals(listOf("경제 뉴스"), captor.secondValue)
    }

    @Test
    fun `LLM 폴백(호출 실패) 결과는 기억하지 않고 다음에 다시 묻는다`() = runTest {
        llmKeeps("정치 뉴스", "배우 근황", judged = false) // 실패 → 전부 통과
        assertEquals(listOf(politics, entertainByLlm), processor.filterByLLM(listOf(politics, entertainByLlm)))

        llmKeeps("정치 뉴스")
        assertEquals(listOf(politics), processor.filterByLLM(listOf(politics, entertainByLlm)))
        verify(llmService, times(2)).filterNewsTitles(any())
    }

    @Test
    fun `빈 목록은 아무것도 호출하지 않는다`() = runTest {
        assertEquals(emptyList(), processor.filterByLLM(emptyList()))
        verify(llmService, never()).filterNewsTitles(any())
    }
}
