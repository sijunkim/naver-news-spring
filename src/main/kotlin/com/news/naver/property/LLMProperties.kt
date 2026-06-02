package com.news.naver.property

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * LLM 호출 관련 설정.
 *
 * 환경변수는 provider별 명시적 이름(GROQ_API_KEY, OPENAI_API_KEY 등)을 쓰고,
 * 코드는 [provider] 값으로 어떤 provider를 사용할지 선택한다.
 * 새 provider 추가 시 [providers] map에 한 항목만 더 등록하면 된다.
 *
 * 모든 등록 provider는 OpenAI Chat Completion 와이어 포맷을 따른다고 가정한다
 * (Groq, OpenAI 등). 다른 와이어 포맷 provider는 별도 client 구현이 필요하다.
 *
 * @property provider 사용 중인 provider 이름 (예: "groq", "openai")
 * @property enabled LLM 호출 기능 활성화 여부
 * @property temperature 응답 창의성 제어 (0.0~2.0)
 * @property maxTokens 필터링 호출의 최대 토큰 수
 * @property summaryMaxTokens 요약 호출의 최대 토큰 수
 * @property providers provider 이름 → 자격증명 매핑
 */
@ConfigurationProperties(prefix = "llm")
data class LLMProperties(
    val provider: String = "groq",
    val enabled: Boolean = true,
    val temperature: Double = 0.3,
    val maxTokens: Int = 1000,
    val summaryMaxTokens: Int = 1500,
    val providers: Map<String, ProviderConfig> = emptyMap()
) {
    data class ProviderConfig(
        val apiKey: String = "",
        val apiUrl: String = "",
        val model: String = ""
    )

    /**
     * [provider] 가 가리키는 자격증명. 등록되지 않은 provider면 예외.
     */
    val active: ProviderConfig
        get() = providers[provider]
            ?: error("Unknown LLM provider: '$provider'. Registered: ${providers.keys}")

    /**
     * LLM 기능이 동작 가능한지 확인. 활성 플래그가 켜져 있고, 현재 provider의 api key가 비어있지 않아야 한다.
     */
    fun isEnabled(): Boolean = enabled && active.apiKey.isNotBlank()
}
