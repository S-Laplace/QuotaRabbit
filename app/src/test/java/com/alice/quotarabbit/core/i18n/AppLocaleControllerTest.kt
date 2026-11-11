package com.alice.quotarabbit.core.i18n

import android.app.LocaleManager
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import android.os.LocaleList
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.settings.LanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class AppLocaleControllerTest {
    @Test
    fun `localized context resolves simplified chinese resources`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.SimplifiedChinese,
        )

        assertEquals("设置", context.getString(R.string.settings_title))
    }

    @Test
    fun `localized context preserves activity ownership chain`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val localized = AppLocaleController.localizedContext(activity, LanguagePreference.Korean)
        assertSame(activity, (localized as ContextWrapper).baseContext)
    }

    @Test
    fun `localized context resolves english resources`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.English,
        )

        assertEquals("Settings", context.getString(R.string.settings_title))
    }

    @Test
    fun `localized context for system locale does not reuse previous override`() {
        val systemLocale = AppLocaleController.supportedSystemLocaleList(LocaleList.getDefault())[0]
        val previousOverride = if (systemLocale.language == "en") {
            LanguagePreference.SimplifiedChinese
        } else {
            LanguagePreference.English
        }
        val overriddenContext = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = previousOverride,
        )

        val systemContext = AppLocaleController.localizedContext(
            context = overriddenContext,
            preference = LanguagePreference.System,
        )

        assertNotEquals(systemLocale.language, overriddenContext.resources.configuration.locales[0].language)
        assertEquals(systemLocale.language, systemContext.resources.configuration.locales[0].language)
    }

    @Test
    fun `system language resolution falls back to english when unsupported`() {
        assertEquals(
            "en",
            AppLocaleController.supportedSystemLocaleList(LocaleList.forLanguageTags("ja-JP"))
                .toLanguageTags(),
        )
    }

    @Test
    fun `system language resolution keeps supported simplified chinese`() {
        assertEquals(
            "zh-CN",
            AppLocaleController.supportedSystemLocaleList(LocaleList.forLanguageTags("zh-CN,en"))
                .toLanguageTags(),
        )
    }

    @Test
    fun `app language applies korean and restores system default`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(LocaleManager::class.java)
        AppLocaleController.apply(context, LanguagePreference.Korean)
        assertEquals("ko", manager.applicationLocales.toLanguageTags())
        AppLocaleController.apply(context, LanguagePreference.System)
        assertEquals("", manager.applicationLocales.toLanguageTags())
    }

    @Test
    fun `korean system locale remains supported`() {
        assertEquals(
            "ko",
            AppLocaleController.supportedSystemLocaleList(LocaleList.forLanguageTags("ko-KR"))
                .toLanguageTags(),
        )
    }

    @Test
    fun `korean localized context resolves translated settings`() {
        val context = AppLocaleController.localizedContext(
            RuntimeEnvironment.getApplication(),
            LanguagePreference.Korean,
        )
        assertEquals("설정", context.getString(R.string.settings_title))
    }
}