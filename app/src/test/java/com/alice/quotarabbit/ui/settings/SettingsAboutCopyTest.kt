package com.alice.quotarabbit.ui.settings

import com.alice.quotarabbit.R
import com.alice.quotarabbit.core.i18n.AppLocaleController
import com.alice.quotarabbit.domain.settings.LanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class SettingsAboutCopyTest {
    @Test
    fun `english about copy avoids mvp and provider-specific emphasis`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.English,
        )

        val version = context.getString(R.string.settings_about_version_format, "0.1.0", "MVP")
        val dataSource = context.getString(R.string.settings_about_data_source)
        val privacy = context.getString(R.string.settings_about_privacy)

        assertEquals("QuotaRabbit 0.1.0", version)
        assertEquals("Quota data comes from each provider's official APIs.", dataSource)
        assertEquals(
            "Account credentials are stored securely on this device. QuotaRabbit connects to provider services to fetch usage data and, once configured, to GitHub to check for app updates.",
            privacy,
        )
        assertFalse(dataSource.contains("OpenAI", ignoreCase = true))
        assertFalse(privacy.contains("token usage", ignoreCase = true))
    }

    @Test
    fun `english about copy exposes github repository link`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.English,
        )

        assertEquals("GitHub repository", context.getString(R.string.settings_about_repository_label))
        assertEquals("Open GitHub repository", context.getString(R.string.settings_about_repository_content_description))
        assertEquals("GitHub repository · Coming soon", context.getString(R.string.settings_about_repository_unavailable))
    }

    @Test
    fun `korean about copy uses QuotaRabbit and explains pending repository`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.Korean,
        )

        assertEquals("QuotaRabbit 0.1.0", context.getString(R.string.settings_about_version_format, "0.1.0", "debug"))
        assertTrue(context.getString(R.string.settings_about_privacy).contains("QuotaRabbit"))
        assertEquals("GitHub 저장소 · 준비 중", context.getString(R.string.settings_about_repository_unavailable))
        assertTrue(context.getString(R.string.settings_update_repository_unavailable).contains("GitHub"))
    }

    @Test
    fun `simplified chinese about copy avoids mvp and provider-specific emphasis`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.SimplifiedChinese,
        )

        val version = context.getString(R.string.settings_about_version_format, "0.1.0", "MVP")
        val dataSource = context.getString(R.string.settings_about_data_source)
        val privacy = context.getString(R.string.settings_about_privacy)

        assertEquals("QuotaRabbit 0.1.0", version)
        assertEquals("额度数据来自各供应商官方 API。", dataSource)
        assertEquals(
            "账户登录凭据安全地存储在本设备上。QuotaRabbit 通过服务提供商接口查询用量，并在连接 GitHub 仓库后检查应用更新。",
            privacy,
        )
        assertFalse(dataSource.contains("OpenAI", ignoreCase = true))
        assertFalse(privacy.contains("token", ignoreCase = true))
        assertTrue(privacy.contains("本设备"))
    }

    @Test
    fun `simplified chinese about copy exposes github repository link`() {
        val context = AppLocaleController.localizedContext(
            context = RuntimeEnvironment.getApplication(),
            preference = LanguagePreference.SimplifiedChinese,
        )

        assertEquals("GitHub 仓库", context.getString(R.string.settings_about_repository_label))
        assertEquals("打开 GitHub 仓库", context.getString(R.string.settings_about_repository_content_description))
        assertEquals("GitHub 仓库 · 筹备中", context.getString(R.string.settings_about_repository_unavailable))
    }
}
