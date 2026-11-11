package com.alice.quotarabbit.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MultiAccountWidgetTest {
    @Test
    fun `legacy single account configuration remains effective`() {
        val configuration = WidgetQuotaConfiguration("codex", "local-1", listOf("weekly"))
        assertEquals(listOf(WidgetAccountSelection("codex", "local-1")), configuration.effectiveAccounts)
        assertEquals(configuration, mutablePreferencesOf().apply {
            writeWidgetQuotaConfiguration(configuration)
        }.toWidgetQuotaConfiguration())
    }

    @Test
    fun `selected accounts persist in user order without changing legacy fields`() {
        val accounts = listOf(
            WidgetAccountSelection("codex", "local-3"),
            WidgetAccountSelection("deepseek", "local-1"),
            WidgetAccountSelection("codex", "local-2"),
        )
        val configuration = WidgetQuotaConfiguration(selectedAccounts = accounts)
        val preferences = mutablePreferencesOf().apply { writeWidgetQuotaConfiguration(configuration) }
        assertEquals(accounts, preferences.toWidgetQuotaConfiguration().effectiveAccounts)
        assertFalse(preferences.toWidgetQuotaConfiguration().isDefault)
    }

    @Test
    fun `multi-account state round trips identity state fields and successful refresh`() {
        val rows = listOf(row("codex", "1"), row("deepseek", "2").copy(status = WidgetQuotaStatus.AuthRequired))
        val state = WidgetQuotaStateFactory().unconfigured(true).copy(isUnconfigured = false, accounts = rows)
        val preferences = mutablePreferencesOf().apply { writeWidgetQuotaState(state) }
        assertEquals(rows, preferences.toWidgetQuotaState().accounts)
    }

    @Test
    fun `deleting one selected account keeps other account data and configuration`() {
        val rows = listOf(row("codex", "1"), row("deepseek", "2"), row("codex", "3"))
        val configuration = WidgetQuotaConfiguration(selectedAccounts = rows.map {
            WidgetAccountSelection(it.providerId, it.localAccountId)
        })
        val preferences = mutablePreferencesOf().apply {
            writeWidgetQuotaConfiguration(configuration)
            writeWidgetQuotaState(WidgetQuotaStateFactory().unconfigured(true).copy(isUnconfigured = false, accounts = rows))
        }
        assertTrue(preferences.clearWidgetQuotaStateIfAccountMatches("deepseek", "2"))
        assertEquals(listOf("1", "3"), preferences.toWidgetQuotaState().accounts.map { it.localAccountId })
        assertEquals(listOf("1", "3"), preferences.toWidgetQuotaConfiguration().effectiveAccounts.map { it.localAccountId })
        assertFalse(preferences.toWidgetQuotaState().isUnconfigured)
    }

    @Test
    fun `adaptive account capacity shows four rows when tall and reports hidden accounts`() {
        assertEquals(1, widgetAccountCapacity(DpSize(180.dp, 60.dp)))
        assertEquals(2, widgetAccountCapacity(DpSize(300.dp, 150.dp)))
        assertEquals(4, widgetAccountCapacity(DpSize(300.dp, 250.dp)))
    }

    @Test
    fun `large fonts reduce account rows instead of clipping quota and status`() {
        assertEquals(1, widgetAccountCapacity(DpSize(180.dp, 60.dp), 1.5f))
        assertEquals(1, widgetAccountCapacity(DpSize(300.dp, 150.dp), 1.5f))
        assertEquals(3, widgetAccountCapacity(DpSize(300.dp, 250.dp), 1.5f))
        assertEquals(2, widgetAccountCapacity(DpSize(300.dp, 250.dp), 2f))
    }

    @Test
    fun `account selection caps and preserves order`() {
        val accounts = (1..5).map { WidgetAccountSelection("codex", "$it") }
        assertEquals(accounts.take(4), toggleWidgetAccountSelection(accounts.take(4), accounts[4]))
        assertEquals(listOf(accounts[0], accounts[2]), toggleWidgetAccountSelection(accounts.take(3), accounts[1]))
    }

    private fun row(provider: String, id: String) = WidgetAccountState(
        providerId = provider,
        localAccountId = id,
        providerName = provider,
        accountName = "Account $id",
        status = WidgetQuotaStatus.Fresh,
        tone = WidgetQuotaTone.Success,
        fields = listOf(WidgetField("five_hour", false, 65, null, null, null, WidgetQuotaTone.Success)),
        lastSuccessfulRefreshAt = Instant.parse("2026-10-09T00:00:00Z"),
    )
}
