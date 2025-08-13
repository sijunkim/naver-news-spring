package com.news.naver.common

import java.net.URI
import java.security.MessageDigest

object HashUtils {

    fun normalizeUrl(raw: String): String {
        // 1) 공백 정리 + 스킴 보정
        val trimmed = raw.trim()
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }

        return try {
            val uri = URI(withScheme)

            // 2) 호스트 정규화: 소문자 + www./m. 제거
            val host = (uri.host ?: "")
                .lowercase()
                .removePrefix("www.")
                .removePrefix("m.")

            // 3) 경로 정규화: 비어 있으면 "/", 연속 슬래시 정리, 트레일링 슬래시 제거
            val rawPath = uri.path ?: "/"
            val singleSlashPath = rawPath.replace(Regex("/+"), "/")
            val path = singleSlashPath.ifBlank { "/" }
            val pathNoTrailing = if (path.length > 1) path.trimEnd('/') else path

            // 4) 포트 유지(있으면), 쿼리/프래그먼트 제거
            val scheme = (uri.scheme ?: "https").lowercase()
            val port = if (uri.port == -1) -1 else uri.port

            // 5) 새로운 표준 URI 구성 (userinfo는 배제)
            val normalized = if (port == -1) {
                URI(scheme, host, pathNoTrailing, null).toString()
            } else {
                URI(scheme, null, host, port, pathNoTrailing, null, null).toString()
            }

            normalized
        } catch (_: Exception) {
            // 파싱 실패 시: 최소한의 정규화 (소문자화 + 트림)
            trimmed.lowercase()
        }
    }

    // 이미 있으실 수도 있지만, 참고용으로 함께 둡니다.
    fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}