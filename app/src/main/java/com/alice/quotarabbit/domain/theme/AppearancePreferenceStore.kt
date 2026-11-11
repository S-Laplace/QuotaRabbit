package com.alice.quotarabbit.domain.theme

import com.alice.quotarabbit.domain.settings.LanguagePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AppearancePreferenceStore {
    val themeMode: Flow<ThemeMode>
    val languagePreference: Flow<LanguagePreference>
        get() = flowOf(LanguagePreference.System)

    suspend fun setLanguagePreference(preference: LanguagePreference) = Unit
    suspend fun setThemeMode(mode: ThemeMode)
}
