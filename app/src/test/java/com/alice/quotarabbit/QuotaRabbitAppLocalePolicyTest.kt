package com.alice.quotarabbit

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class QuotaRabbitAppLocalePolicyTest {
    @Test
    fun `application startup clears persisted platform language override instead of applying saved in app choice`() {
        val source = sourceFile("src/main/java/com/alice/quotarabbit/QuotaRabbitApp.kt").readText()

        assertFalse(
            "QuotaRabbit follows the system language now; startup must not re-apply old persisted " +
                "in-app language overrides.",
            source.contains("AppLocaleController.applyToApp"),
        )
        assertFalse(
            "Startup should not read the old language preference just to mutate app locales.",
            source.contains("languagePreferences.languagePreference()"),
        )
        org.junit.Assert.assertTrue(
            "Startup must restore the saved app language before UI renders.",
            source.contains("AppLocaleController.apply(this, initialLanguagePreference)"),
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
