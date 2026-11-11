package com.alice.quotarabbit.widget

import java.time.Instant
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind

enum class WidgetQuotaStatus {
    NoAccount,
    Loading,
    Fresh,
    PossiblyStale,
    Expired,
    AuthRequired,
    ErrorWithLastKnownGood,
    NoData,
    Disconnected,
}

enum class WidgetQuotaTone {
    Neutral,
    Success,
    Warning,
    Danger,
}

enum class WidgetClickTarget {
    Home,
    AddAccount,
}

/** Display-safe remaining quota, balance, consumed request count or per-model fraction. */
data class WidgetField(
    val windowId: String,
    val isBalance: Boolean,
    val percent: Int?,
    val balanceAmount: String?,
    val balanceCurrency: String?,
    val resetAt: Instant?,
    val tone: WidgetQuotaTone,
    // Provider-reported window duration. Positional ids (Codex "five_hour"/"weekly") pick their
    // label from this instead of the slot they arrived in.
    val limitWindowSeconds: Int? = null,
    val displayKind: QuotaWindowDisplayKind = if (isBalance) QuotaWindowDisplayKind.Balance else QuotaWindowDisplayKind.Percent,
    val usedCount: Int? = null,
    val limitCount: Int? = null,
    val modelName: String? = null,
)

/** Clipped local quota facts for one selected account; never contains a session or diagnostics. */
data class WidgetAccountState(
    val providerId: String,
    val localAccountId: String,
    val providerName: String,
    val accountName: String?,
    val status: WidgetQuotaStatus,
    val tone: WidgetQuotaTone,
    val fields: List<WidgetField>,
    val lastSuccessfulRefreshAt: Instant? = null,
) {
    // Public provider branding only; no provider client, DTO or session is reachable here.
    val providerIconRes: Int?
        get() = com.alice.quotarabbit.providers.ProviderRegistry.iconFor(
            com.alice.quotarabbit.domain.model.ProviderId(providerId),
        )
}

data class WidgetQuotaState(
    val status: WidgetQuotaStatus,
    val providerName: String,
    val providerId: String?,
    val localAccountId: String?,
    val accountName: String?,
    val tone: WidgetQuotaTone,
    val clickTarget: WidgetClickTarget,
    val fields: List<WidgetField> = emptyList(),
    // true = 微件未选账号，渲染引导态而非数据。
    val isUnconfigured: Boolean = false,
    // 引导态时区分「无任何账号」(false) 与「有账号待配置」(true)。
    val hasAccounts: Boolean = true,
    @get:androidx.annotation.DrawableRes val providerIconRes: Int? = null,
    val accounts: List<WidgetAccountState> = emptyList(),
    val lastSuccessfulRefreshAt: Instant? = null,
)

internal fun String?.finiteWidgetBalanceAmount(): String? =
    this?.takeIf { it.toDoubleOrNull()?.isFinite() == true }
