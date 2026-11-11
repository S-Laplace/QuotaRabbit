package com.alice.quotarabbit.widget

import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.providers.ProviderRegistry
import java.time.Instant

data class WidgetPreviewSpec(
    val name: String,
    val variant: WidgetLayoutVariant,
    val widthDp: Int,
    val heightDp: Int,
    val state: WidgetQuotaState,
)

/** Shared, credential-free specimens for the real RemoteViews preview and instrumentation checks. */
object WidgetPreviewFixtures {
    fun specs(now: Instant = Instant.now()): List<WidgetPreviewSpec> {
        fun percent(id: String, value: Int, duration: Int) = WidgetField(
            windowId = id, isBalance = false, percent = value, balanceAmount = null,
            balanceCurrency = null, resetAt = now.plusSeconds(5400), tone = WidgetQuotaTone.Success,
            limitWindowSeconds = duration,
        )
        val primary = percent("five_hour", 82, 18000)
        val weekly = percent("weekly", 56, 604800)
        val balance = WidgetField(
            "balance", true, null, "42.50", "USD", null, WidgetQuotaTone.Success,
            displayKind = QuotaWindowDisplayKind.Balance,
        )
        val count = WidgetField(
            "cursor_usage", false, null, null, null, null, WidgetQuotaTone.Success,
            displayKind = QuotaWindowDisplayKind.UsageCount, usedCount = 125, limitCount = 500,
        )
        fun account(provider: String, id: String, name: String, fields: List<WidgetField>) = WidgetAccountState(
            provider, "preview-$id", ProviderRegistry.all.first { it.providerId.value == provider }.displayName,
            name, WidgetQuotaStatus.Fresh, WidgetQuotaTone.Success, fields, now.minusSeconds(120),
        )
        val accounts = listOf(
            account("codex", "personal", "Personal", listOf(primary, weekly)),
            account("codex", "work", "Work", listOf(primary.copy(percent = 62), weekly.copy(percent = 27, tone = WidgetQuotaTone.Warning))),
            account("codex", "team", "Team", listOf(primary.copy(percent = 6, tone = WidgetQuotaTone.Danger), weekly.copy(percent = 9, tone = WidgetQuotaTone.Danger)))
                .copy(tone = WidgetQuotaTone.Danger),
            account("deepseek", "balance", "API balance", listOf(balance)),
        )
        val state = WidgetQuotaState(
            status = WidgetQuotaStatus.Fresh, providerName = "Codex", providerId = "codex",
            localAccountId = "preview-personal", accountName = "Personal", tone = WidgetQuotaTone.Success,
            clickTarget = WidgetClickTarget.Home, fields = listOf(primary, weekly),
            providerIconRes = ProviderRegistry.iconFor(ProviderId("codex")),
        )
        val multi = state.copy(accounts = accounts)
        val states = multi.copy(accounts = listOf(
            accounts[0],
            accounts[1].copy(status = WidgetQuotaStatus.ErrorWithLastKnownGood,
                lastSuccessfulRefreshAt = now.minusSeconds(600)),
            accounts[2].copy(status = WidgetQuotaStatus.AuthRequired, tone = WidgetQuotaTone.Danger),
            accounts[3].copy(status = WidgetQuotaStatus.PossiblyStale,
                lastSuccessfulRefreshAt = now.minusSeconds(2700)),
        ))
        val unavailable = multi.copy(accounts = listOf(
            accounts[0],
            accounts[1].copy(status = WidgetQuotaStatus.Expired,
                lastSuccessfulRefreshAt = now.minusSeconds(10800)),
            accounts[2].copy(status = WidgetQuotaStatus.NoData, fields = emptyList(),
                lastSuccessfulRefreshAt = null),
            accounts[3].copy(status = WidgetQuotaStatus.NoAccount, fields = emptyList(),
                lastSuccessfulRefreshAt = null),
        ))
        return listOf(
            WidgetPreviewSpec("3x1", WidgetLayoutVariant.ThreeByOne, 252, 110, state.copy(fields = listOf(primary))),
            WidgetPreviewSpec("4x1", WidgetLayoutVariant.FourByOne, 340, 110, state),
            WidgetPreviewSpec("3x2", WidgetLayoutVariant.ThreeByTwo, 252, 210, state.copy(fields = listOf(primary, weekly, balance))),
            WidgetPreviewSpec("4x2", WidgetLayoutVariant.FourByTwo, 340, 210, state.copy(fields = listOf(primary, weekly, count, balance))),
            WidgetPreviewSpec("multi_3x1", WidgetLayoutVariant.ThreeByOne, 180, 60, multi),
            WidgetPreviewSpec("multi_4x1", WidgetLayoutVariant.FourByOne, 300, 60, multi),
            WidgetPreviewSpec("multi_3x2", WidgetLayoutVariant.ThreeByTwo, 180, 150, multi),
            WidgetPreviewSpec("multi_4x2", WidgetLayoutVariant.FourByTwo, 300, 150, multi),
            WidgetPreviewSpec("multi_4x3", WidgetLayoutVariant.FourByThree, 300, 250, multi),
            WidgetPreviewSpec("multi_states", WidgetLayoutVariant.FourByThree, 300, 250, states),
            WidgetPreviewSpec("multi_unavailable", WidgetLayoutVariant.FourByThree, 300, 250, unavailable),
            WidgetPreviewSpec("multi_counts", WidgetLayoutVariant.FourByThree, 300, 250,
                multi.copy(accounts = listOf(accounts[0], account("cursor", "count", "Request account", listOf(count))))),
            WidgetPreviewSpec("unconfigured", WidgetLayoutVariant.ThreeByTwo, 252, 210,
                state.copy(status = WidgetQuotaStatus.NoAccount, providerId = null, localAccountId = null,
                    accountName = null, fields = emptyList(), isUnconfigured = true)),
        )
    }

    fun spec(name: String): WidgetPreviewSpec = specs().firstOrNull { it.name == name } ?: specs().first { it.name == "multi_4x3" }
}
