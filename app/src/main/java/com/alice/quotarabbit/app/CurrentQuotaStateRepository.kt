package com.alice.quotarabbit.app

import com.alice.quotarabbit.data.currency.ExchangeRateReader
import com.alice.quotarabbit.data.local.dao.ProviderAccountDao
import com.alice.quotarabbit.data.local.dao.QuotaSnapshotDao
import com.alice.quotarabbit.data.local.dao.RefreshAttemptDao
import com.alice.quotarabbit.data.preferences.CurrentAccountReader
import com.alice.quotarabbit.data.preferences.CurrentAccountSelection
import com.alice.quotarabbit.data.repository.toDomain
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceReader
import com.alice.quotarabbit.domain.currency.CurrencyPreferences
import com.alice.quotarabbit.domain.currency.ExchangeRates
import com.alice.quotarabbit.domain.currency.withConvertedBalance
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.AccountStatus
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.quota.CurrentQuotaStateFactory
import com.alice.quotarabbit.domain.quota.HomeDashboardState
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.settings.DefaultPrimaryQuotaWindowPreferenceReader
import com.alice.quotarabbit.domain.settings.NotificationPreferenceReader
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.domain.settings.PrimaryQuotaWindowPreferenceReader
import com.alice.quotarabbit.notification.StatusNotificationStateLoader
import com.alice.quotarabbit.ui.home.HomeCurrentQuotaStateLoader
import java.time.Clock

internal class CurrentQuotaStateRepository(
    private val currentAccountReader: CurrentAccountReader,
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val currentQuotaStateFactory: CurrentQuotaStateFactory = CurrentQuotaStateFactory(),
    private val primaryQuotaWindowPreferenceReader: PrimaryQuotaWindowPreferenceReader =
        DefaultPrimaryQuotaWindowPreferenceReader,
    private val notificationPreferenceReader: NotificationPreferenceReader =
        DefaultStatusNotificationPreferenceReader,
    private val clock: Clock,
    private val currencyPreferenceReader: CurrencyPreferenceReader = DefaultCurrencyPreferenceReader,
    private val exchangeRateReader: ExchangeRateReader = DefaultExchangeRateReader,
) : HomeCurrentQuotaStateLoader, StatusNotificationStateLoader {
    override suspend fun loadDashboardState(): HomeDashboardState {
        val selection = currentAccountReader.currentAccountSelection()
        val primaryWindowId = primaryQuotaWindowPreferenceReader.primaryQuotaWindowId()
        // Only normalized cached snapshots and safe attempts cross this boundary. Enumerating Home
        // never reads credentials or changes selection, and failures retain each account's own LKG.
        val states = providerAccountDao.listAll().map { it.toDomain() }
            .filterNot { it.status == AccountStatus.Deleted }
            .sortedWith(compareBy<ProviderAccount> { if (it.providerId.value == "codex") 0 else 1 }
                .thenBy { it.providerId.value }.thenBy { it.createdAt }.thenBy { it.localAccountId.value })
            .map { loadState(it, primaryWindowId) }
        val currentState = states.firstOrNull {
            it.account?.providerId == selection?.providerId &&
                it.account?.localAccountId == selection?.localAccountId
        } ?: unauthenticatedState()
        return HomeDashboardState(
            currentState = currentState,
            accountStates = states,
            providerCurrentSelections = currentAccountReader.currentProviderSelections().filter { selected ->
                states.any { it.account?.providerId == selected.providerId &&
                    it.account?.localAccountId == selected.localAccountId }
            },
        )
    }

    override suspend fun loadCurrentState(): CurrentQuotaState {
        val selection = currentAccountReader.currentAccountSelection()
            ?: return unauthenticatedState()
        val account = loadAccount(selection)
            ?: return unauthenticatedState()
        return loadState(
            account = account,
            primaryWindowId = primaryQuotaWindowPreferenceReader.primaryQuotaWindowId(),
        )
    }

    override suspend fun loadStatusNotificationState(refreshedState: CurrentQuotaState): CurrentQuotaState {
        val preferences = notificationPreferenceReader.notificationPreferences()
        val configuredAccount = preferences.persistentNotificationAccount
            ?.let { selection ->
                loadAccount(
                    CurrentAccountSelection(
                        providerId = selection.providerId,
                        localAccountId = selection.localAccountId,
                    ),
                )
            }
        val fallbackSelection = currentAccountReader.currentAccountSelection()
        val account = configuredAccount
            ?: fallbackSelection?.let { loadAccount(it) }
            ?: return unauthenticatedState()

        val state = loadState(
            account = account,
            primaryWindowId = preferences.persistentNotificationWindowId,
        )
        val currency = currencyPreferenceReader.currencyPreferences()
        val rates = exchangeRateReader.currentRates()
        val resolvedPrimary = resolveNotificationPrimaryWindow(state)
        return state.copy(
            primaryWindow = resolvedPrimary?.withConvertedBalance(currency.targetCurrency, rates),
        )
    }

    suspend fun loadAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        primaryWindowId: QuotaWindowId,
    ): CurrentQuotaState {
        val account = loadAccount(
            CurrentAccountSelection(
                providerId = providerId,
                localAccountId = localAccountId,
            ),
        ) ?: return unauthenticatedState()
        return loadState(
            account = account,
            primaryWindowId = primaryWindowId,
        )
    }

    private suspend fun loadAccount(selection: CurrentAccountSelection): ProviderAccount? =
        providerAccountDao.getById(selection.localAccountId.value)
            ?.toDomain()
            ?.takeIf { it.providerId == selection.providerId }

    private suspend fun loadState(
        account: ProviderAccount,
        primaryWindowId: QuotaWindowId,
    ): CurrentQuotaState {
        val latestSnapshot = quotaSnapshotDao.getLatestForAccount(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
        )?.toDomain()
        val latestAttempt = refreshAttemptDao.getLatestForAccount(
            providerId = account.providerId.value,
            localAccountId = account.localAccountId.value,
        )?.toDomain()

        return currentQuotaStateFactory.create(
            account = account,
            latestSnapshot = latestSnapshot,
            latestAttempt = latestAttempt,
            now = clock.instant(),
            primaryWindowId = primaryWindowId,
        )
    }

    private fun unauthenticatedState(): CurrentQuotaState =
        currentQuotaStateFactory.create(
            account = null,
            latestSnapshot = null,
            latestAttempt = null,
            now = clock.instant(),
        )
}

/**
 * Resolves the primary window for the persistent status notification.
 *
 * When the stored persistentNotificationWindowId belongs to a previous account and is absent from
 * the current account's snapshot, [CurrentQuotaState.primaryWindow] is null. The same applies when
 * the id is present but the provider stopped sending that slot (Codex without a secondary_window).
 * Rather than showing no quota in the notification, fall back to the account's own available
 * primary-candidate window, or else the first window in the snapshot.
 */
internal fun resolveNotificationPrimaryWindow(state: CurrentQuotaState): QuotaWindow? {
    val chosen = state.primaryWindow?.takeUnless { it.availability == QuotaWindowAvailability.Missing }
    if (chosen != null) return chosen
    val windows = state.snapshot?.windows.orEmpty()
    return windows.firstOrNull { it.isPrimaryCandidate && it.availability == QuotaWindowAvailability.Available }
        ?: windows.firstOrNull { it.isPrimaryCandidate }
        ?: windows.firstOrNull()
}

private object DefaultStatusNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}

private object DefaultCurrencyPreferenceReader : CurrencyPreferenceReader {
    override suspend fun currencyPreferences() = CurrencyPreferences()
}

private object DefaultExchangeRateReader : ExchangeRateReader {
    override suspend fun currentRates(): ExchangeRates? = null
}
