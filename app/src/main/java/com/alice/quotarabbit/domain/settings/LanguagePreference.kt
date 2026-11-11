package com.alice.quotarabbit.domain.settings

enum class LanguagePreference(val storageName: String, val localeTag: String?) {
    System("system", null),
    SimplifiedChinese("zh_hans", "zh-CN"),
    English("en", "en"),
    Korean("ko", "ko");

    companion object {
        fun fromStorage(value: String?): LanguagePreference =
            entries.firstOrNull { it.storageName == value } ?: System
    }
}
