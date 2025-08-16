package com.news.naver.controller

import com.news.naver.service.ManualService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(ManualController::class)
class ManualControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var manualService: ManualService

    @Test
    fun `POST breaking-news-jobs는 서비스를 호출하고 성공 메시지를 반환한다`() = runTest {
        // given
        whenever(manualService.runBreakingNewsPoll()).thenReturn(Unit)

        // when & then
        webTestClient.post().uri("/manual/news/breaking")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("Breaking news poll triggered.")
    }

    @Test
    fun `POST exclusive-news-jobs는 서비스를 호출하고 성공 메시지를 반환한다`() = runTest {
        // given
        whenever(manualService.runExclusiveNewsPoll()).thenReturn(Unit)

        // when & then
        webTestClient.post().uri("/manual/news/exclusive")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("Exclusive news poll triggered.")
    }

    @Test
    fun `DELETE spam-keywords는 서비스를 호출하고 성공 메시지를 반환한다`() = runTest {
        // given
        whenever(manualService.resetSpamKeywords()).thenReturn(5L)

        // when & then
        webTestClient.delete().uri("/manual/keywords/spam")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("Deleted 5 spam keyword entries.")
    }

    @Test
    fun `DELETE polls-timestamp는 서비스를 호출하고 성공 메시지를 반환한다`() = runTest {
        // given
        whenever(manualService.deletePollTimestamps()).thenReturn(2L)

        // when & then
        webTestClient.delete().uri("/manual/polls/timestamp")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("Deleted 2 poll timestamp entries.")
    }

    @Test
    fun `DELETE runtime-data는 서비스를 호출하고 성공 메시지를 반환한다`() = runTest {
        // given
        whenever(manualService.resetRuntimeData()).thenReturn(Unit)

        // when & then
        webTestClient.delete().uri("/manual/runtime-data")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("Runtime data reset.")
    }
}
