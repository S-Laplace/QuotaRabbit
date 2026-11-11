package com.alice.quotarabbit

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityFontPolicyTest {
    @Test
    fun `main activity uses the system default typography without legacy font preferences`() {
        val source = sourceFile("src/main/java/com/alice/quotarabbit/MainActivity.kt").readText()

        assertTrue(
            "The application should use the theme's Android default sans-serif typography.",
            source.contains("QuotaRabbitTheme(themeMode = themeMode)"),
        )
        assertFalse(
            "Legacy font schemes and preferences must not override the application typography.",
            source.contains("MonoFocusGeistMono") || source.contains("fontSchemePreferenceFlow()") ||
                source.contains("toQuotaRabbitFontScheme"),
        )
    }

    private fun sourceFile(path: String): File {
        val moduleFile = File(path)
        if (moduleFile.exists()) return moduleFile

        val rootFile = File("app", path)
        if (rootFile.exists()) return rootFile

        error("Cannot locate $path from ${File(".").absolutePath}")
    }
}
