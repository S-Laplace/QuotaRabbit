package com.alice.quotarabbit.ui.settings

import com.alice.quotarabbit.domain.settings.LanguagePreference
import com.alice.quotarabbit.domain.theme.AppearancePreferenceStore
import com.alice.quotarabbit.domain.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelLanguageTest {
    @get:Rule val dispatcherRule = SettingsViewModelTest.MainDispatcherRule()

    private class FakeAppearanceStore(initial: LanguagePreference) : AppearancePreferenceStore {
        override val themeMode = flowOf(ThemeMode.SYSTEM)
        private val mutableLanguage = MutableStateFlow(initial)
        override val languagePreference = mutableLanguage
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setLanguagePreference(preference: LanguagePreference) {
            mutableLanguage.value = preference
        }
    }

    @Test fun `loadSettings restores selected language`() = runTest {
        val store = FakeAppearanceStore(LanguagePreference.Korean)
        val model = SettingsViewModel(appearancePreferenceStore = store)
        model.loadSettings()
        runCurrent()
        assertEquals(LanguagePreference.Korean, model.uiState.value.appearance.language)
    }

    @Test fun `selecting language saves and dismisses dialog`() = runTest {
        val store = FakeAppearanceStore(LanguagePreference.System)
        val model = SettingsViewModel(appearancePreferenceStore = store)
        model.requestChoiceDialog(SettingsChoiceDialog.Language)
        model.updateLanguage(LanguagePreference.English)
        runCurrent()
        assertEquals(LanguagePreference.English, model.uiState.value.appearance.language)
        assertEquals(LanguagePreference.English, store.languagePreference.value)
        assertNull(model.uiState.value.pendingChoiceDialog)
    }
}
