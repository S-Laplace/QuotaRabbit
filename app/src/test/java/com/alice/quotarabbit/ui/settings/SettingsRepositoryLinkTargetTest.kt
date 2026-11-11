package com.alice.quotarabbit.ui.settings

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class SettingsRepositoryLinkTargetTest {
    @Test
    fun `repository link opens the QuotaRabbit GitHub repository`() {
        val intent = SettingsRepositoryLinkTarget.openIntent()
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("https://github.com/S-Laplace/QuotaRabbit", intent?.data?.toString())
    }
}
