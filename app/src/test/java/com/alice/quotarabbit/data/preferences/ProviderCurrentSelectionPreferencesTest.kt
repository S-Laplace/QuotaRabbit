package com.alice.quotarabbit.data.preferences

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProviderCurrentSelectionPreferencesTest {
    private fun selected(provider: String, account: String) =
        CurrentAccountSelection(ProviderId(provider), LocalAccountId(account))

    @Test fun `selection survives reads and remains independent by provider`() = runTest {
        val preferences = CurrentAccountPreferences.create(
            RuntimeEnvironment.getApplication().preferencesDataStoreFile("selected-per-provider-test.preferences_pb"))
        preferences.updateCurrentAccountSelection(selected("claude", "c1"))
        preferences.updateCurrentAccountSelection(selected("codex", "x1"))
        preferences.updateCurrentAccountSelection(selected("codex", "x2"))
        val saved = preferences.currentProviderSelections().associateBy { it.providerId.value }
        assertEquals(2, saved.size)
        assertEquals("c1", saved.getValue("claude").localAccountId.value)
        assertEquals("x2", saved.getValue("codex").localAccountId.value)
        assertTrue(saved.getValue("codex").selectionOrder > saved.getValue("claude").selectionOrder)
        preferences.clearProviderSelectionIfMatches(selected("codex", "x1"))
        assertEquals(2, preferences.currentProviderSelections().size)
        preferences.clearProviderSelectionIfMatches(selected("codex", "x2"))
        assertEquals(listOf("claude"), preferences.currentProviderSelections().map { it.providerId.value })
    }

    @Test fun `legacy global current remains pinned when another provider is selected`() = runTest {
        val preferences = CurrentAccountPreferences.create(
            RuntimeEnvironment.getApplication().preferencesDataStoreFile("selected-migration-test.preferences_pb"))
        preferences.dataStore.edit {
            it[CurrentAccountPreferences.Keys.CURRENT_PROVIDER_ID] = "claude"
            it[CurrentAccountPreferences.Keys.CURRENT_LOCAL_ACCOUNT_ID] = "old"
        }
        assertEquals("old", preferences.currentProviderSelections().single().localAccountId.value)
        preferences.updateCurrentAccountSelection(selected("codex", "new"))
        val saved = preferences.currentProviderSelections().associateBy { it.providerId.value }
        assertEquals(2, saved.size)
        assertEquals("old", saved.getValue("claude").localAccountId.value)
        assertTrue(saved.getValue("codex").selectionOrder > saved.getValue("claude").selectionOrder)
    }
}
