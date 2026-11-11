package com.alice.quotarabbit.data.preferences

import androidx.datastore.preferences.preferencesDataStoreFile
import com.alice.quotarabbit.domain.settings.LanguagePreference
import com.alice.quotarabbit.domain.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 needs Java 21; project is pinned to Java 17.
@Config(sdk = [35])
class AppearancePreferencesTest {
    private fun store(file: String) = AppearancePreferences.create(
        file = RuntimeEnvironment.getApplication().preferencesDataStoreFile(file),
    )

    @Test fun `defaults to system when unset`() = runTest {
        assertEquals(ThemeMode.SYSTEM, store("appearance-default.preferences_pb").themeMode.first())
    }

    @Test fun `persists app language and restores system default`() = runTest {
        val subject = store("language-roundtrip.preferences_pb")
        assertEquals(LanguagePreference.System, subject.languagePreference.first())
        for (language in listOf(LanguagePreference.Korean, LanguagePreference.English, LanguagePreference.System)) {
            subject.setLanguagePreference(language)
            assertEquals(language, subject.languagePreference.first())
        }
    }

    @Test fun `persists and reads back each mode`() = runTest {
        val subject = store("appearance-roundtrip.preferences_pb")
        for (mode in ThemeMode.entries) {
            subject.setThemeMode(mode)
            assertEquals(mode, subject.themeMode.first())
        }
    }
}
