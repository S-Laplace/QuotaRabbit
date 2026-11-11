package com.alice.quotarabbit.domain.account

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentAccountOrderingTest {
    private data class Item(val provider: String, val id: String)
    private fun selected(provider: String, id: String, order: Long) =
        ProviderCurrentSelection(ProviderId(provider), LocalAccountId(id), order)
    private fun List<Item>.ordered(selections: List<ProviderCurrentSelection>) =
        sortedByCurrentSelection(selections, { ProviderId(it.provider) }, { LocalAccountId(it.id) })

    @Test fun `all providers show latest Set Current first`() {
        val accounts = listOf(Item("codex", "old"), Item("claude", "old"), Item("claude", "selected"), Item("codex", "selected"))
        val selections = listOf(selected("claude", "selected", 1), selected("codex", "selected", 2))
        assertEquals(listOf("codex:selected", "claude:selected", "codex:old", "claude:old"),
            accounts.ordered(selections).map { "${it.provider}:${it.id}" })
    }

    @Test fun `changing current within provider removes previous pin`() {
        val accounts = listOf(Item("codex", "first"), Item("codex", "second"), Item("claude", "selected"))
        val selections = listOf(selected("codex", "second", 3), selected("claude", "selected", 2))
        assertEquals(listOf("second", "selected", "first"), accounts.ordered(selections).map { it.id })
        assertFalse(selections.isCurrent(ProviderId("codex"), LocalAccountId("first")))
        assertTrue(selections.isCurrent(ProviderId("codex"), LocalAccountId("second")))
    }

    @Test fun `provider filter still pins selected account and preserves stable remainder`() {
        val selections = listOf(selected("codex", "third", 5), selected("claude", "one", 4))
        val accounts = listOf(Item("codex", "first"), Item("codex", "second"), Item("codex", "third"))
        assertEquals(listOf("third", "first", "second"), accounts.ordered(selections).map { it.id })
    }
}
