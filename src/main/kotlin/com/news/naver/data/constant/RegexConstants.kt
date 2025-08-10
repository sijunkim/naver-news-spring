package com.news.naver.data.constant

object RegexConstants {
    const val HTML_TAG_REGEX = "(<([^>]+)>)"
    const val URL_PREFIX_REGEX = "^(https?://)?(www\\.)?(news\\.)?(view\\.)?(post\\.)?(photo\\.)?(photos\\.)?(blog\\.)?"
    const val NEWS_TYPE_TAG_REGEX = "\\[(<b>)?(속보|단독)(</b>)?\\]|\"|'|`"
}
