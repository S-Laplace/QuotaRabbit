package com.alice.quotarabbit

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLocalePolicyTest {
    @Test
    fun `main activity uses localized resources without triggering platform locale changes during composition`() {
        val source = sourceFile("src/main/java/com/alice/quotarabbit/MainActivity.kt").readText()

        assertFalse(
            "Applying LocaleManager from Compose state can recreate MainActivity before DataStore emits " +
                "the saved preference, causing a System-target locale loop.",
            source.contains("LaunchedEffect(languagePreference)"),
        )
        assertFalse(
            "MainActivity must not collect the old in-app language override; QuotaRabbit follows the " +
                "system language now.",
            source.contains("languagePreferenceFlow()"),
        )
        org.junit.Assert.assertTrue(
            "The Compose tree must use resources for the persisted app language.",
            source.contains("AppLocaleController.localizedContext(this@MainActivity, language)"),
        )
        assertFalse(
            "Applying Android per-app locales during Compose recomposition can loop Activity recreation.",
            source.contains("AppLocaleController.apply(this@MainActivity"),
        )
        assertTrue(
            "Composition should update formatting without requesting Activity recreation.",
            source.contains("AppLocaleController.applyProcessDefaultLocale(language)"),
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
