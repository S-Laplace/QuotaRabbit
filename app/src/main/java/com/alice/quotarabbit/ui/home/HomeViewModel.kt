package com.alice.quotarabbit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.AccountStatus
import com.alice.quotarabbit.providers.ProviderRegistry
import com.alice.quotarabbit.ui.quota.quotaWindowLabelRes
import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.quota.HomeDashboardState
import com.alice.quotarabbit.domain.account.ProviderCurrentSelection
import com.alice.quotarabbit.domain.account.isCurrent
import com.alice.quotarabbit.domain.account.sortedByCurrentSelection
import com.alice.quotarabbit.domain.quota.CurrentQuotaStatus
import com.alice.quotarabbit.domain.quota.Credits
import com.alice.quotarabbit.data.currency.ExchangeRateReader
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceReader
import com.alice.quotarabbit.domain.currency.CurrencyPreferences
import com.alice.quotarabbit.domain.currency.ExchangeRates
import com.alice.quotarabbit.domain.currency.withConvertedBalance
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.domain.settings.NotificationPreferenceReader
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.ui.providerPlanDisplayName
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun interface HomeCurrentQuotaStateLoader {
    suspend fun loadCurrentState(): CurrentQuotaState

    suspend fun loadDashboardState(): HomeDashboardState {
        val current = loadCurrentState()
        return HomeDashboardState(current, listOfNotNull(current.takeIf { it.account != null }))
    }
}

fun interface HomeRefreshUseCase {
    suspend fun refreshCurrentState(): CurrentQuotaState

    suspend fun refreshDashboard(onAccountRefreshed: suspend (CurrentQuotaState) -> Unit): CurrentQuotaState =
        refreshCurrentState().also { onAccountRefreshed(it) }
}

data class HomeTrendQuery(
    val windowId: String,
    val displayKind: QuotaWindowDisplayKind,
    val useModelBucketSum: Boolean,
)

fun interface HomeTrendHistoryLoader {
    suspend fun loadTrend(accountId: LocalAccountId?, query: HomeTrendQuery): List<HomeTrendPointUi>

    /** Per-account, long-horizon cached history for the Home activity heatmap. */
    suspend fun loadActivity(providerId: ProviderId, accountId: LocalAccountId, query: HomeTrendQuery):
        List<HomeTrendPointUi> = emptyList()
}

private object NoopHomeCurrentQuotaStateLoader : HomeCurrentQuotaStateLoader {
    override suspend fun loadCurrentState(): CurrentQuotaState =
        com.alice.quotarabbit.domain.quota.CurrentQuotaStateFactory().create(
            account = null,
            latestSnapshot = null,
            latestAttempt = null,
            now = Instant.EPOCH,
        )
}

private object NoopHomeRefreshUseCase : HomeRefreshUseCase {
    override suspend fun refreshCurrentState(): CurrentQuotaState =
        NoopHomeCurrentQuotaStateLoader.loadCurrentState()
}

private object NoopHomeTrendHistoryLoader : HomeTrendHistoryLoader {
    override suspend fun loadTrend(accountId: LocalAccountId?, query: HomeTrendQuery): List<HomeTrendPointUi> = emptyList()
}

private object DefaultHomeNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}

private object DefaultHomeCurrencyPreferenceReader : CurrencyPreferenceReader {
    override suspend fun currencyPreferences(): CurrencyPreferences = CurrencyPreferences()
}

private object DefaultHomeExchangeRateReader : ExchangeRateReader {
    override suspend fun currentRates(): ExchangeRates? = null
}

class HomeViewModel(
    private val currentQuotaStateLoader: HomeCurrentQuotaStateLoader = NoopHomeCurrentQuotaStateLoader,
    private val refreshUseCase: HomeRefreshUseCase = NoopHomeRefreshUseCase,
    private val trendHistoryLoader: HomeTrendHistoryLoader = NoopHomeTrendHistoryLoader,
    private val notificationPreferenceReader: NotificationPreferenceReader = DefaultHomeNotificationPreferenceReader,
    private val currencyPreferenceReader: CurrencyPreferenceReader = DefaultHomeCurrencyPreferenceReader,
    private val exchangeRateReader: ExchangeRateReader = DefaultHomeExchangeRateReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState.loading())
    private var currentTrendAccountId: LocalAccountId? = null
    private var currentTrendQuery: HomeTrendQuery? = null
    private var notificationPreferences = NotificationPreferences()
    private var currencyPreferences: CurrencyPreferences = CurrencyPreferences()
    private var exchangeRates: ExchangeRates? = null
    private val trendPointsByAccount = mutableMapOf<LocalAccountId?, List<HomeTrendPointUi>>()
    private val activityPointsByAccount = mutableMapOf<Pair<ProviderId, LocalAccountId>, List<HomeTrendPointUi>>()
    private var activityLoadJob: Job? = null
    private var currentStateLoadJob: Job? = null
    private var trendLoadJob: Job? = null
    private var manualRefreshSuccessCount = 0
    private var dashboardState: HomeDashboardState? = null
    private var refreshJob: Job? = null
    private val refreshingAccounts = mutableSetOf<Pair<ProviderId, LocalAccountId>>()

    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun updateCurrentQuotaState(state: CurrentQuotaState, isRefreshing: Boolean = false) {
        val existing = dashboardState
        dashboardState = HomeDashboardState(
            currentState = state,
            accountStates = if (existing == null) listOfNotNull(state.takeIf { it.account != null })
                else existing.accountStates.map { if (it.sameAccountAs(state)) state else it },
            providerCurrentSelections = existing?.providerCurrentSelections.orEmpty(),
        )
        renderDashboard(isRefreshing, reloadTrend = true)
    }

    private fun renderDashboard(isRefreshing: Boolean = false, reloadTrend: Boolean = false) {
        val dashboard = dashboardState ?: return
        val state = dashboard.currentState.takeIf { it.account != null }
            ?: dashboard.accountStates.firstOrNull() ?: dashboard.currentState
        currentTrendAccountId = state.account?.localAccountId
        currentTrendQuery = state.trendWindow()?.toTrendQuery()
        if (reloadTrend) clearTrendCacheFor(currentTrendAccountId)
        val selections = dashboard.providerCurrentSelections.ifEmpty {
            listOfNotNull(dashboard.currentState.account?.let {
                ProviderCurrentSelection(it.providerId, it.localAccountId, 0L)
            })
        }
        _uiState.value = mapToUiState(state = state, isRefreshing = isRefreshing).copy(
            accountCards = dashboard.accountStates.mapNotNull { accountState ->
                val account = accountState.account ?: return@mapNotNull null
                HomeDashboardAccountUi(
                    providerId = account.providerId,
                    localAccountId = account.localAccountId,
                    providerDisplayName = ProviderRegistry.displayNameFor(account.providerId),
                    isCurrent = selections.isCurrent(account.providerId, account.localAccountId),
                    state = mapToUiState(accountState, account.key() in refreshingAccounts),
                    isDisconnected = account.status == AccountStatus.Disabled,
                    activityPoints = activityPointsByAccount[account.providerId to account.localAccountId].orEmpty(),
                )
            }.sortedByCurrentSelection(selections, { it.providerId }, { it.localAccountId }),
        )
        if (reloadTrend && state.account != null && currentTrendQuery != null) {
            loadTrend()
        }
        if (reloadTrend) loadAccountActivity(dashboard.accountStates)
    }

    /**
     * Loads the latest persisted snapshot for display. Deliberately does NOT hit the network: opening
     * Home (and every ON_RESUME / tab re-entry) must not trigger an API refresh, which previously
     * caused frequent calls when switching pages. Freshness comes from manual pull-to-refresh and the
     * periodic background worker; this only reflects whatever those have already written.
     */
    fun loadCurrentState() {
        if (currentStateLoadJob?.isActive == true) {
            return
        }
        currentStateLoadJob = viewModelScope.launch {
            loadNotificationPreferences()
            val persistedState = loadPersistedDashboardOrNull()
            if (persistedState != null) {
                dashboardState = persistedState
                renderDashboard(isRefreshing = refreshJob?.isActive == true, reloadTrend = true)
            } else {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    fun refreshNow() {
        if (refreshJob?.isActive == true) return
        refreshingAccounts += dashboardState?.accountStates.orEmpty().mapNotNull { state ->
            state.account?.takeIf { it.status == AccountStatus.Active || it.status == AccountStatus.NeedsReauth }?.key()
        }
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        renderDashboard(isRefreshing = true)
        refreshJob = viewModelScope.launch {
            try {
                loadNotificationPreferences()
                var anySuccess = false
                val refreshedState = refreshUseCase.refreshDashboard { state ->
                    anySuccess = anySuccess || state.status.isRefreshSweepSuccess()
                    state.account?.key()?.let { refreshingAccounts.remove(it) }
                    val existing = dashboardState
                    dashboardState = HomeDashboardState(
                        currentState = if (existing == null || existing.currentState.sameAccountAs(state)) state else existing.currentState,
                        accountStates = existing?.accountStates?.map { if (it.sameAccountAs(state)) state else it }
                            ?: listOfNotNull(state.takeIf { it.account != null }),
                        providerCurrentSelections = existing?.providerCurrentSelections.orEmpty(),
                    )
                    renderDashboard(isRefreshing = true)
                }
                if (anySuccess) manualRefreshSuccessCount += 1
                updateCurrentQuotaState(refreshedState)
            } finally {
                refreshingAccounts.clear()
                renderDashboard(isRefreshing = false)
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    private suspend fun loadNotificationPreferences() {
        runCatching {
            notificationPreferenceReader.notificationPreferences()
        }.onSuccess { preferences ->
            notificationPreferences = preferences
        }
        currencyPreferences = currencyPreferenceReader.currencyPreferences()
        exchangeRates = exchangeRateReader.currentRates()
    }

    /** Test-only: populate preference fields via injected readers without triggering a full state load. */
    internal suspend fun loadPreferencesForTest() {
        notificationPreferences = notificationPreferenceReader.notificationPreferences()
        currencyPreferences = currencyPreferenceReader.currencyPreferences()
        exchangeRates = exchangeRateReader.currentRates()
    }

    private suspend fun loadPersistedDashboardOrNull(): HomeDashboardState? =
        try {
            currentQuotaStateLoader.loadDashboardState()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    fun mapToUiState(
        state: CurrentQuotaState,
        isRefreshing: Boolean = false,
    ): HomeUiState {
        if (state.status == CurrentQuotaStatus.Unauthenticated) {
            return HomeUiState.unauthenticated(isRefreshing = isRefreshing)
        }

        val contentStatus = state.status.toHomeStatus()
        val statusTitleResId = state.statusTitleResId()
        val statusDescriptionResId = state.statusDescriptionResId()
        val allWindows = state.allWindows()
        val quotaCards = allWindows
            // A slot the provider simply did not send (Codex without a secondary_window) is noise as a
            // "--" placeholder card; decode failures stay visible so a broken payload is noticeable.
            .filterNot { it.availability == QuotaWindowAvailability.Missing }
            .map { it.withConvertedBalance(currencyPreferences.targetCurrency, exchangeRates) }
            .map { window -> window.toQuotaCard() }
        val effectiveRefreshing = isRefreshing || state.status == CurrentQuotaStatus.Loading

        return HomeUiState(
            contentStatus = contentStatus,
            statusTitleResId = statusTitleResId,
            statusDescriptionResId = statusDescriptionResId,
            errorMessageResId = state.error?.safeMessageKey?.toErrorMessageResId(),
            account = state.account?.let {
                HomeAccountUi(
                    displayName = it.displayName,
                    avatarInitial = it.avatarInitial,
                    providerId = it.providerId,
                    avatarColorKey = it.avatarColorKey,
                    accentColorId = it.accentColorId,
                    manualRenewalDate = it.manualRenewalDate,
                    bankedResetCount = state.snapshot?.codexBenefits?.bankedResetCount,
                    providerRenewalAt = state.snapshot?.codexBenefits?.subscriptionRenewsAt,
                    subscriptionActiveUntil = state.snapshot?.codexBenefits?.subscriptionActiveUntil,
                    activeUntilFromToken = state.snapshot?.codexBenefits?.activeUntilFromToken == true,
                    planType = providerPlanDisplayName(it.providerId, state.snapshot?.planType),
                    credits = state.snapshot?.credits.toHomeCredits(),
                    providerIconResId = ProviderRegistry.iconFor(it.providerId),
                )
            },
            quotaCards = quotaCards,
            trend = HomeTrendUi(
                metricLabelResId = state.trendWindow()?.displayKind.metricLabelResId(),
                points = trendPointsByAccount[state.account?.localAccountId].orEmpty(),
            ),
            loading = if (
                state.status == CurrentQuotaStatus.Loading &&
                quotaCards.isEmpty()
            ) {
                HomeLoadingUi(
                    titleResId = R.string.home_loading_card_title,
                    descriptionResId = R.string.home_loading_card_description,
                )
            } else {
                null
            },
            refresh = HomeRefreshUi(
                titleResId = if (effectiveRefreshing) R.string.home_state_loading_title else statusTitleResId,
                descriptionResId = if (effectiveRefreshing) {
                    R.string.home_state_loading_description
                } else {
                    statusDescriptionResId
                },
                buttonTextResId = if (effectiveRefreshing) R.string.home_refreshing else R.string.home_refresh,
                lastSuccessfulRefreshAt = state.snapshot?.fetchedAt ?: state.account?.lastSuccessfulRefreshAt,
                lastAttemptFinishedAt = state.latestAttempt?.finishedAt,
            ),
            primaryAction = state.primaryAction(),
            secondaryAction = state.secondaryAction(),
            isRefreshing = effectiveRefreshing,
            manualRefreshSuccessCount = manualRefreshSuccessCount,
        )
    }

    private fun CurrentQuotaStatus.toHomeStatus(): HomeContentStatus =
        when (this) {
            CurrentQuotaStatus.Unauthenticated -> HomeContentStatus.Unauthenticated
            CurrentQuotaStatus.Loading -> HomeContentStatus.Loading
            CurrentQuotaStatus.Fresh -> HomeContentStatus.Fresh
            CurrentQuotaStatus.PossiblyStale -> HomeContentStatus.PossiblyStale
            CurrentQuotaStatus.Expired -> HomeContentStatus.Expired
            CurrentQuotaStatus.AuthRequired -> HomeContentStatus.AuthRequired
            CurrentQuotaStatus.ErrorWithLastKnownGood -> HomeContentStatus.ErrorWithLastKnownGood
            CurrentQuotaStatus.NoData -> HomeContentStatus.NoData
        }

    private fun CurrentQuotaStatus.isRefreshSweepSuccess(): Boolean =
        this == CurrentQuotaStatus.Fresh || this == CurrentQuotaStatus.PossiblyStale

    private fun CurrentQuotaState.statusTitleResId(): Int =
        when (status) {
            CurrentQuotaStatus.Unauthenticated -> R.string.home_state_unauthenticated_title
            CurrentQuotaStatus.Loading -> R.string.home_state_loading_title
            CurrentQuotaStatus.Fresh -> R.string.home_state_fresh_title
            CurrentQuotaStatus.PossiblyStale -> R.string.home_state_possibly_stale_title
            CurrentQuotaStatus.Expired -> R.string.home_state_expired_title
            CurrentQuotaStatus.AuthRequired -> R.string.home_state_auth_required_title
            CurrentQuotaStatus.ErrorWithLastKnownGood -> R.string.home_state_error_lkg_title
            CurrentQuotaStatus.NoData -> R.string.home_state_no_data_title
        }

    private fun CurrentQuotaState.statusDescriptionResId(): Int =
        when (status) {
            CurrentQuotaStatus.Unauthenticated -> R.string.home_state_unauthenticated_description
            CurrentQuotaStatus.Loading -> R.string.home_state_loading_description
            CurrentQuotaStatus.Fresh -> R.string.home_state_fresh_description
            CurrentQuotaStatus.PossiblyStale -> R.string.home_state_possibly_stale_description
            CurrentQuotaStatus.Expired -> R.string.home_state_expired_description
            CurrentQuotaStatus.AuthRequired -> R.string.home_state_auth_required_description
            CurrentQuotaStatus.ErrorWithLastKnownGood -> R.string.home_state_error_lkg_description
            CurrentQuotaStatus.NoData -> R.string.home_state_no_data_description
        }

    private fun CurrentQuotaState.primaryAction(): HomeActionUi? =
        when (status) {
            CurrentQuotaStatus.Unauthenticated,
            CurrentQuotaStatus.AuthRequired -> HomeActionUi(
                kind = HomeActionKind.LoginToCodex,
                labelResId = R.string.home_login_to_codex,
            )
            else -> null
        }

    private fun CurrentQuotaState.secondaryAction(): HomeActionUi? = null

    private fun CurrentQuotaState.allWindows(): List<QuotaWindow> =
        (listOfNotNull(primaryWindow) + secondaryWindows).distinctBy { it.windowId.value }

    private fun loadTrend() {
        val accountId = currentTrendAccountId
        val query = currentTrendQuery ?: return
        trendLoadJob?.cancel()
        trendLoadJob = viewModelScope.launch {
            val points = try {
                trendHistoryLoader.loadTrend(accountId = accountId, query = query)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                emptyList()
            }
            trendPointsByAccount[accountId] = points
            if (currentTrendAccountId == accountId) {
                _uiState.value = _uiState.value.copy(
                    trend = _uiState.value.trend.copy(points = points),
                )
            }
        }
    }

    private fun loadAccountActivity(states: List<CurrentQuotaState>) {
        activityLoadJob?.cancel()
        activityLoadJob = viewModelScope.launch {
            states.forEach { state ->
                val account = state.account ?: return@forEach
                val query = state.trendWindow()?.toTrendQuery() ?: return@forEach
                val key = account.providerId to account.localAccountId
                val points = try {
                    trendHistoryLoader.loadActivity(account.providerId, account.localAccountId, query)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                activityPointsByAccount[key] = points
                _uiState.value = _uiState.value.copy(accountCards = _uiState.value.accountCards.map { card ->
                    if (card.providerId to card.localAccountId == key) card.copy(activityPoints = points)
                    else card
                })
            }
        }
    }

    private fun clearTrendCacheFor(accountId: LocalAccountId?) {
        trendPointsByAccount.remove(accountId)
    }

    // The trend follows the window the user sees as primary. CurrentQuotaState.primaryWindow is only
    // non-null when the snapshot contains the globally-configured primary window id (default
    // "five_hour"), which never matches non-Codex providers (DeepSeek "balance", Antigravity families,
    // etc.). Fall back to the snapshot's own primary-candidate window so every provider charts usage.
    private fun CurrentQuotaState.trendWindow(): QuotaWindow? =
        primaryWindow
            ?: secondaryWindows.firstOrNull { it.isPrimaryCandidate }
            ?: secondaryWindows.firstOrNull()

    private fun QuotaWindow.toTrendQuery(): HomeTrendQuery =
        HomeTrendQuery(
            windowId = windowId.value,
            displayKind = displayKind,
            useModelBucketSum = usesModelBucketSum,
        )

    private fun QuotaWindowDisplayKind?.metricLabelResId(): Int =
        when (this) {
            QuotaWindowDisplayKind.Balance -> R.string.home_trend_metric_spend
            QuotaWindowDisplayKind.UsageCount -> R.string.home_trend_metric_calls
            else -> R.string.home_trend_metric_usage
        }

    private fun QuotaWindow.toQuotaCard(): HomeQuotaCardUi {
        val quotaStatus = toQuotaStatus()
        val hasUsableValues = availability == QuotaWindowAvailability.Available ||
            availability == QuotaWindowAvailability.Depleted
        return HomeQuotaCardUi(
            windowId = windowId.value,
            titleResId = quotaWindowLabelRes(windowId.value, limitWindowSeconds),
            displayKind = displayKind,
            usedPercent = usedPercent.takeIf { hasUsableValues },
            balanceAmount = balanceAmount.finiteAmountOrNull().takeIf { hasUsableValues },
            balanceCurrency = balanceCurrency,
            usedCount = usedCount.takeIf { hasUsableValues },
            limitCount = limitCount.takeIf { hasUsableValues },
            subLabel = subLabel,
            isPrimary = isPrimaryCandidate,
            resetAt = resetAt,
            status = quotaStatus,
            tone = quotaStatus.toTone(),
            statusLabelResId = quotaStatus.labelResId(),
            originalBalanceAmount = originalBalanceAmount.finiteAmountOrNull().takeIf { hasUsableValues },
            originalBalanceCurrency = originalBalanceCurrency,
            grantedBalance = grantedBalance.finiteAmountOrNull().takeIf { hasUsableValues },
            toppedUpBalance = toppedUpBalance.finiteAmountOrNull().takeIf { hasUsableValues },
            modelBuckets = modelBuckets.takeIf { hasUsableValues }.orEmpty(),
        )
    }

    private fun QuotaWindow.toQuotaStatus(): HomeQuotaStatus {
        if (availability == QuotaWindowAvailability.Depleted) return HomeQuotaStatus.Exhausted
        if (availability != QuotaWindowAvailability.Available) return HomeQuotaStatus.Unavailable
        if (displayKind == QuotaWindowDisplayKind.Balance) {
            val amount = balanceAmount?.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return HomeQuotaStatus.Unavailable
            return when {
                amount <= 0.0 -> HomeQuotaStatus.Exhausted
                amount <= notificationPreferences.balanceWarningThreshold -> HomeQuotaStatus.Warning
                amount <= notificationPreferences.balanceCautionThreshold -> HomeQuotaStatus.Caution
                else -> HomeQuotaStatus.Normal
            }
        }
        // Count-only providers need valid status copy without adding a percentage to their UI model.
        val percent = displayPercent?.toDouble() ?: if (displayKind == QuotaWindowDisplayKind.UsageCount &&
            usedCount != null && usedCount >= 0 && limitCount != null && limitCount > 0
        ) {
            (limitCount.toLong() - usedCount.toLong()).coerceAtLeast(0).toDouble() / limitCount * 100
        } else null
        return remainingQuotaStatus(percent, notificationPreferences)
    }

    internal fun testQuotaStatusFor(window: QuotaWindow): HomeQuotaStatus =
        window.withConvertedBalance(currencyPreferences.targetCurrency, exchangeRates).toQuotaStatus()

    private fun String?.finiteAmountOrNull(): String? =
        takeIf { it?.toDoubleOrNull()?.isFinite() == true }

    private fun Credits?.toHomeCredits(): HomeCreditsUi =
        when {
            this == null || !hasCredits -> HomeCreditsUi.Unavailable
            unlimited -> HomeCreditsUi.Unlimited
            balance != null -> HomeCreditsUi.Balance(balance)
            else -> HomeCreditsUi.Unavailable
        }

    private fun HomeQuotaStatus.toTone(): HomeStatusTone =
        when (this) {
            HomeQuotaStatus.Normal -> HomeStatusTone.Success
            HomeQuotaStatus.Caution -> HomeStatusTone.Warning
            HomeQuotaStatus.Warning -> HomeStatusTone.Danger
            HomeQuotaStatus.Exhausted -> HomeStatusTone.Danger
            HomeQuotaStatus.Unavailable -> HomeStatusTone.Neutral
        }

    private fun HomeQuotaStatus.labelResId(): Int =
        when (this) {
            HomeQuotaStatus.Normal -> R.string.home_quota_status_normal
            HomeQuotaStatus.Caution -> R.string.home_quota_status_caution
            HomeQuotaStatus.Warning -> R.string.home_quota_status_warning
            HomeQuotaStatus.Exhausted -> R.string.home_quota_status_exhausted
            HomeQuotaStatus.Unavailable -> R.string.home_quota_status_unavailable
        }

    private fun String.toErrorMessageResId(): Int =
        when (this) {
            "error_auth_required" -> R.string.error_auth_required
            "error_network" -> R.string.error_network
            else -> R.string.error_unknown
        }

    private fun com.alice.quotarabbit.domain.model.ProviderAccount.key() = providerId to localAccountId

    private fun CurrentQuotaState.sameAccountAs(other: CurrentQuotaState): Boolean =
        account != null && account.providerId == other.account?.providerId &&
            account.localAccountId == other.account.localAccountId

    companion object {
        fun factory(
            currentQuotaStateLoader: HomeCurrentQuotaStateLoader,
            refreshUseCase: HomeRefreshUseCase,
            trendHistoryLoader: HomeTrendHistoryLoader = NoopHomeTrendHistoryLoader,
            notificationPreferenceReader: NotificationPreferenceReader = DefaultHomeNotificationPreferenceReader,
            currencyPreferenceReader: CurrencyPreferenceReader = DefaultHomeCurrencyPreferenceReader,
            exchangeRateReader: ExchangeRateReader = DefaultHomeExchangeRateReader,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(
                        currentQuotaStateLoader = currentQuotaStateLoader,
                        refreshUseCase = refreshUseCase,
                        trendHistoryLoader = trendHistoryLoader,
                        notificationPreferenceReader = notificationPreferenceReader,
                        currencyPreferenceReader = currencyPreferenceReader,
                        exchangeRateReader = exchangeRateReader,
                    ) as T
            }

    }
}
