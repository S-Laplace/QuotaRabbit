package com.alice.quotarabbit.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLanguageEntryPolicyTest {
    @Test
    fun `settings appearance opens app language dialog`() {
        val source = sourceFile("src/main/java/com/alice/quotarabbit/ui/settings/SettingsScreen.kt").readText()

        assertTrue(
            "The Appearance card should open the language choice dialog.",
            source.contains("SettingsChoiceDialog.Language"),
        )
        assertTrue(
            "The language options must update the saved language preference.",
            source.contains("onLanguageSelected(language)"),
        )
        assertTrue(
            "The language picker should expose accessible radio options.",
            source.contains("role = Role.RadioButton"),
        )
    }

    @Test
    fun `english system theme label keeps three options on one row at normal width`() {
        val resources = sourceFile("src/main/res/values/strings.xml").readText()
        val appearanceSource = sourceFile("src/main/java/com/alice/quotarabbit/ui/settings/SettingsAppearanceAlertsCards.kt").readText()

        assertTrue(
            "The English system label must stay concise so the theme control matches the Korean horizontal layout.",
            resources.contains("<string name=\"settings_appearance_system\">System</string>"),
        )
        assertTrue(
            "Keep horizontal segmented mode with an accessible stacked fallback for small widths and large fonts.",
            appearanceSource.contains("val stacked = !labelsFit || density.fontScale >= 1.3f"),
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
