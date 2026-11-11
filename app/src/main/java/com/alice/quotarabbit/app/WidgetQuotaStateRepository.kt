package com.alice.quotarabbit.app

import com.alice.quotarabbit.data.currency.ExchangeRateReader
import com.alice.quotarabbit.data.local.dao.ProviderAccountDao
import com.alice.quotarabbit.data.local.dao.QuotaSnapshotDao
import com.alice.quotarabbit.data.local.dao.RefreshAttemptDao
import com.alice.quotarabbit.data.repository.toDomain
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceReader
import com.alice.quotarabbit.domain.currency.CurrencyPreferences
import com.alice.quotarabbit.domain.currency.ExchangeRates
import com.alice.quotarabbit.domain.currency.withConvertedBalance
import com.alice.quotarabbit.domain.model.AccountStatus
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.quota.CurrentQuotaStateFactory
import com.alice.quotarabbit.domain.settings.NotificationPreferenceReader
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.widget.WidgetQuotaConfiguration
import com.alice.quotarabbit.widget.WidgetQuotaState
import com.alice.quotarabbit.widget.WidgetQuotaStateFactory
import com.alice.quotarabbit.widget.WidgetQuotaStateLoader
import com.alice.quotarabbit.widget.WidgetAccountSelection
import com.alice.quotarabbit.widget.WidgetAccountState
import com.alice.quotarabbit.widget.WidgetQuotaStatus
import com.alice.quotarabbit.widget.WidgetQuotaTone
import java.time.Clock

internal class WidgetQuotaStateRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val currentQuotaStateFactory: CurrentQuotaStateFactory = CurrentQuotaStateFactory(),
    private val widgetQuotaStateFactory: WidgetQuotaStateFactory = WidgetQuotaStateFactory(),
    private val notificationPreferenceReader: NotificationPreferenceReader = DefaultWidgetNotificationPreferenceReader,
    private val currencyPreferenceReader: CurrencyPreferenceReader = DefaultWidgetCurrencyPreferenceReader,
    private val exchangeRateReader: ExchangeRateReader = DefaultWidgetExchangeRateReader,
    private val clock: Clock,
) : WidgetQuotaStateLoader {
    override suspend fun loadWidgetQuotaState(configuration: WidgetQuotaConfiguration): WidgetQuotaState {
        val selections = configuration.effectiveAccounts
        if (selections.size > 1) {
            // Each row derives its state from its own local snapshot/attempt. The widget never
            // fetches quota, and one account's missing data cannot substitute another account.
            val rows = selections.map { selection ->
                val state = loadSingleAccount(WidgetQuotaConfiguration(selection.providerId, selection.localAccountId))
                state.toAccountRow(selection)
            }
            return widgetQuotaStateFactory.unconfigured(hasAccounts = true).copy(
                isUnconfigured = false,
                accounts = rows,
            )
        }
        val single = selections.singleOrNull()
        return loadSingleAccount(if (single == null) configuration else configuration.copy(
            providerId = single.providerId,
            localAccountId = single.localAccountId,
            selectedAccounts = emptyList(),
        ))
    }

    private suspend fun loadSingleAccount(configuration: WidgetQuotaConfiguration): WidgetQuotaState {
        val providerId = configuration.providerId?.takeIf { it.isNotBlank() }
        val localAccountId = configuration.localAccountId?.takeIf { it.isNotBlank() }
        if (providerId == null || localAccountId == null) {
            return widgetQuotaStateFactory.unconfigured(hasAccounts = hasSelectableAccount())
        }
        val account = providerAccountDao.getById(localAccountId)
            ?.toDomain()
            ?.takeIf { it.providerId.value == providerId && it.status != AccountStatus.Deleted }
            ?: return widgetQuotaStateFactory.unconfigured(hasAccounts = hasSelectableAccount())

        val latestSnapshot = quotaSnapshotDao.getLatestForAccount(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
        )?.toDomain()
        val latestAttempt = refreshAttemptDao.getLatestForAccount(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
        )?.toDomain()
        // primaryWindow 用于 CurrentQuotaState 内部计算；取第一个已选窗口或默认。
        val primaryWindowId = configuration.selectedWindowIds.firstOrNull()
            ?.let(::QuotaWindowId)
            ?: DEFAULT_WIDGET_PRIMARY_WINDOW_ID
        val currentQuotaState = currentQuotaStateFactory.create(
            account = account,
            latestSnapshot = latestSnapshot,
            latestAttempt = latestAttempt,
            now = clock.instant(),
            primaryWindowId = primaryWindowId,
        )
        // 与首页 / 通知路径一致：余额先按目标货币换算，工厂再与余额阈值比较并显示换算后的值。
        val currency = currencyPreferenceReader.currencyPreferences()
        val rates = exchangeRateReader.currentRates()
        val convertedState = currentQuotaState.let { state ->
            state.copy(
                snapshot = state.snapshot?.copy(
                    windows = state.snapshot.windows.map { it.withConvertedBalance(currency.targetCurrency, rates) },
                ),
                primaryWindow = state.primaryWindow?.withConvertedBalance(currency.targetCurrency, rates),
                secondaryWindows = state.secondaryWindows.map { it.withConvertedBalance(currency.targetCurrency, rates) },
            )
        }
        return widgetQuotaStateFactory.create(
            state = convertedState,
            notificationPreferences = notificationPreferenceReader.notificationPreferences(),
            selectedWindowIds = configuration.selectedWindowIds,
        )
    }

    private suspend fun hasSelectableAccount(): Boolean =
        providerAccountDao.listAll().any { it.toDomain().status != AccountStatus.Deleted }

    private fun WidgetQuotaState.toAccountRow(selection: WidgetAccountSelection) = WidgetAccountState(
        providerId = selection.providerId,
        localAccountId = selection.localAccountId,
        providerName = if (isUnconfigured) selection.providerId else providerName,
        accountName = accountName,
        status = if (isUnconfigured) WidgetQuotaStatus.NoAccount else status,
        tone = if (isUnconfigured) WidgetQuotaTone.Neutral else tone,
        fields = fields,
        lastSuccessfulRefreshAt = lastSuccessfulRefreshAt,
    )
}

private object DefaultWidgetNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}

private object DefaultWidgetCurrencyPreferenceReader : CurrencyPreferenceReader {
    override suspend fun currencyPreferences(): CurrencyPreferences = CurrencyPreferences()
}

private object DefaultWidgetExchangeRateReader : ExchangeRateReader {
    override suspend fun currentRates(): ExchangeRates? = null
}

private val DEFAULT_WIDGET_PRIMARY_WINDOW_ID = QuotaWindowId("five_hour")
