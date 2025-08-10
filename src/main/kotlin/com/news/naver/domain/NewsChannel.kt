package com.news.naver.domain

enum class NewsChannel(val query: String) {
    BREAKING("속보"),
    EXCLUSIVE("단독"),
    DEV("테스트")
}
