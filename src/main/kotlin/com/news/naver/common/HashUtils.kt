package com.news.naver.common

import java.security.MessageDigest

/**
 * 해시 관련 유틸리티 함수를 제공하는 객체입니다.
 */
object HashUtils {
    /**
     * 주어진 문자열에 대해 SHA-256 해시를 계산합니다.
     *
     * @param input 해시를 계산할 문자열
     * @return 계산된 SHA-256 해시 문자열
     */
    fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
