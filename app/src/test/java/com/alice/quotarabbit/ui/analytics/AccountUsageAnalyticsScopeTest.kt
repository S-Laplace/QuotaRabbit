package com.alice.quotarabbit.ui.analytics

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import java.time.Instant
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AccountUsageAnalyticsScopeTest {
    private fun account(localId: String, provider: String): ProviderAccount =
        ProviderAccount.createNew(
            localAccountId = LocalAccountId(localId),
            providerId = ProviderId(provider),
            providerAccountId = null,
            displayName = "Account $localId",
            now = Instant.parse("2026-10-28T00:00:00Z"),
        )

    @Test
    fun onlyTheSelectedCodexAccountCanBeUsed() {
        val current = account("current", "codex")
        val selected = account("selected", "codex")
        assertSame(selected, selectCodexAnalyticsAccount(listOf(current, selected), selected.localAccountId))
    }

    @Test
    fun unknownOrDifferentProviderNeverFallsBackToAnotherAccount() {
        val codex = account("other", "codex")
        val claude = account("selected", "claude")
        assertNull(selectCodexAnalyticsAccount(listOf(codex, claude), claude.localAccountId))
        assertNull(selectCodexAnalyticsAccount(listOf(codex, claude), LocalAccountId("missing")))
        assertNull(selectCodexAnalyticsAccount(emptyList(), LocalAccountId("missing")))
    }
}
