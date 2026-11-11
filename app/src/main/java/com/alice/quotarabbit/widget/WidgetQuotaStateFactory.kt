package com.alice.quotarabbit.widget

import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.quota.CurrentQuotaStatus
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.providers.ProviderRegistry
import com.alice.quotarabbit.domain.model.AccountStatus

class WidgetQuotaStateFactory(
    private val providerDisplayName: String = UNCONFIGURED_HEADER,
) {
    /** 微件未选账号时的引导态。 */
    fun unconfigured(hasAccounts: Boolean): WidgetQuotaState =
        WidgetQuotaState(
            status = WidgetQuotaStatus.NoAccount,
            providerName = providerDisplayName,
            providerId = null,
            localAccountId = null,
            accountName = null,
            tone = WidgetQuotaTone.Neutral,
            // Tapping an unconfigured widget opens the app's Home tab (where the no-account state already
            // surfaces an add-account entry). It must not deep-link to the retired provider-selection
            // screen; the no-accounts hint still tells the user to add an account in-app first.
            clickTarget = WidgetClickTarget.Home,
            fields = emptyList(),
            isUnconfigured = true,
            hasAccounts = hasAccounts,
        )

    fun create(
        state: CurrentQuotaState,
        notificationPreferences: NotificationPreferences = NotificationPreferences(),
        selectedWindowIds: List<String> = emptyList(),
    ): WidgetQuotaState {
        val account = state.account
        if (account == null) {
            return unconfigured(hasAccounts = false)
        }
        val fields = state.buildFields(selectedWindowIds, notificationPreferences)
        val firstField = fields.firstOrNull()
        return WidgetQuotaState(
            status = if (account.status == AccountStatus.Disabled) WidgetQuotaStatus.Disconnected
                else state.status.toWidgetStatus(),
            providerName = ProviderRegistry.displayNameFor(account.providerId),
            providerIconRes = ProviderRegistry.iconFor(account.providerId),
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
            accountName = account.displayName,
            tone = if (account.status == AccountStatus.Disabled) WidgetQuotaTone.Neutral else state.headerTone(firstField),
            clickTarget = WidgetClickTarget.Home,
            fields = fields,
            isUnconfigured = false,
            hasAccounts = true,
            lastSuccessfulRefreshAt = state.snapshot?.fetchedAt ?: account.lastSuccessfulRefreshAt,
        )
    }

    private fun CurrentQuotaState.buildFields(
        selectedWindowIds: List<String>,
        notificationPreferences: NotificationPreferences,
    ): List<WidgetField> {
        val windows = snapshot?.windows.orEmpty()
        val selected = if (selectedWindowIds.isEmpty()) {
            windows
        } else {
            windows.filter { selectedWindowIds.contains(it.windowId.value) }
        }
        return selected
            .filter { it.isDisplayable() }
            .flatMap { window ->
                val isBalance = window.displayKind == QuotaWindowDisplayKind.Balance
                val field = WidgetField(
                    windowId = window.windowId.value,
                    isBalance = isBalance,
                    percent = if (isBalance || window.availability !in USABLE_AVAILABILITIES) null else window.displayPercent,
                    balanceAmount = if (isBalance && window.availability in USABLE_AVAILABILITIES)
                        window.balanceAmount.finiteWidgetBalanceAmount() else null,
                    balanceCurrency = if (isBalance) window.balanceCurrency else null,
                    resetAt = window.resetAt,
                    tone = window.percentTone(notificationPreferences),
                    limitWindowSeconds = window.limitWindowSeconds,
                    displayKind = window.displayKind,
                    usedCount = window.usedCount.takeIf { window.availability in USABLE_AVAILABILITIES },
                    limitCount = window.limitCount.takeIf { window.availability in USABLE_AVAILABILITIES },
                )
                if (window.displayKind == QuotaWindowDisplayKind.MultiModelFraction && window.modelBuckets.isNotEmpty()) {
                    window.modelBuckets.map { model ->
                        val percent = model.remainingFraction.takeIf {
                            it.isFinite() && window.availability in USABLE_AVAILABILITIES
                        }
                            ?.coerceIn(0.0, 1.0)?.times(100)?.toInt()
                        field.copy(
                            modelName = model.displayName,
                            percent = percent,
                            resetAt = model.resetAt,
                            tone = window.copy(usedPercent = percent?.let { 100 - it }).percentTone(notificationPreferences),
                        )
                    }
                } else listOf(field)
            }
            .take(WIDGET_MAX_FIELDS)
    }

    private fun CurrentQuotaStatus.toWidgetStatus(): WidgetQuotaStatus =
        when (this) {
            CurrentQuotaStatus.Unauthenticated -> WidgetQuotaStatus.NoAccount
            CurrentQuotaStatus.Loading -> WidgetQuotaStatus.Loading
            CurrentQuotaStatus.Fresh -> WidgetQuotaStatus.Fresh
            CurrentQuotaStatus.PossiblyStale -> WidgetQuotaStatus.PossiblyStale
            CurrentQuotaStatus.Expired -> WidgetQuotaStatus.Expired
            CurrentQuotaStatus.AuthRequired -> WidgetQuotaStatus.AuthRequired
            CurrentQuotaStatus.ErrorWithLastKnownGood -> WidgetQuotaStatus.ErrorWithLastKnownGood
            CurrentQuotaStatus.NoData -> WidgetQuotaStatus.NoData
        }

    private fun CurrentQuotaState.headerTone(firstField: WidgetField?): WidgetQuotaTone =
        when (status) {
            CurrentQuotaStatus.Unauthenticated,
            CurrentQuotaStatus.PossiblyStale,
            CurrentQuotaStatus.Expired,
            CurrentQuotaStatus.NoData -> WidgetQuotaTone.Neutral
            CurrentQuotaStatus.AuthRequired -> WidgetQuotaTone.Danger
            CurrentQuotaStatus.ErrorWithLastKnownGood -> WidgetQuotaTone.Warning
            CurrentQuotaStatus.Loading,
            CurrentQuotaStatus.Fresh -> firstField?.tone ?: WidgetQuotaTone.Neutral
        }

    private fun QuotaWindow.percentTone(notificationPreferences: NotificationPreferences): WidgetQuotaTone {
        if (availability == QuotaWindowAvailability.Depleted) return WidgetQuotaTone.Danger
        if (displayKind == QuotaWindowDisplayKind.Balance) {
            // Mirror Home's balance bands, in the converted display currency (the repository converts
            // before mapping fields). Exhausted (<= 0) shares Danger with the warning band, matching
            // the quota path above where both severe bands read the same label.
            val amount = balanceAmount.finiteWidgetBalanceAmount()?.toDoubleOrNull()
            return when {
                availability != QuotaWindowAvailability.Available || amount == null -> WidgetQuotaTone.Neutral
                amount <= 0.0 -> WidgetQuotaTone.Danger
                amount <= notificationPreferences.balanceWarningThreshold -> WidgetQuotaTone.Danger
                amount <= notificationPreferences.balanceCautionThreshold -> WidgetQuotaTone.Warning
                else -> WidgetQuotaTone.Success
            }
        }
        val percent = displayPercent
        return when {
            availability != QuotaWindowAvailability.Available || percent == null -> WidgetQuotaTone.Neutral
            percent <= notificationPreferences.limitThreshold -> WidgetQuotaTone.Danger
            percent <= notificationPreferences.warningThreshold -> WidgetQuotaTone.Danger
            percent <= notificationPreferences.cautionThreshold -> WidgetQuotaTone.Warning
            else -> WidgetQuotaTone.Success
        }
    }

    private fun QuotaWindow.isDisplayable(): Boolean = availability != QuotaWindowAvailability.Missing

    private companion object {
        // Header shown on the unconfigured/no-account widget: app branding, not a single provider,
        // since QuotaRabbit now supports multiple AI providers.
        const val UNCONFIGURED_HEADER = "QuotaRabbit"
        val USABLE_AVAILABILITIES = setOf(QuotaWindowAvailability.Available, QuotaWindowAvailability.Depleted)
    }
}
