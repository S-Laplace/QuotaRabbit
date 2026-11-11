package com.alice.quotarabbit.app

import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.refresh.RefreshTrigger
import com.alice.quotarabbit.refresh.RefreshCoordinator
import com.alice.quotarabbit.refresh.MultiAccountRefreshRunner
import com.alice.quotarabbit.refresh.ExchangeRateRefresher
import com.alice.quotarabbit.domain.settings.DEFAULT_PRIMARY_QUOTA_WINDOW_ID
import com.alice.quotarabbit.ui.home.HomeRefreshUseCase

internal class HomeRefreshCoordinatorUseCase(
    private val currentAccountStore: CurrentQuotaRefreshAccountStore,
    private val refreshCoordinator: RefreshCoordinator,
    private val currentQuotaStateLoader: CurrentQuotaStateRepository,
    private val exchangeRateRefresher: ExchangeRateRefresher = ExchangeRateRefresher { },
) : HomeRefreshUseCase {
    override suspend fun refreshDashboard(onAccountRefreshed: suspend (CurrentQuotaState) -> Unit): CurrentQuotaState {
        MultiAccountRefreshRunner(refreshCoordinator, exchangeRateRefresher = exchangeRateRefresher).refresh(
            accounts = currentAccountStore.manuallyRefreshableAccounts(),
            trigger = RefreshTrigger.Manual,
            onAccountRefreshed = { account, _ ->
                onAccountRefreshed(currentQuotaStateLoader.loadAccountState(
                    providerId = account.providerId,
                    localAccountId = account.localAccountId,
                    primaryWindowId = DEFAULT_PRIMARY_QUOTA_WINDOW_ID,
                ))
            },
        )
        return currentQuotaStateLoader.loadCurrentState()
    }

    // Manual pull-to-refresh only. Opening Home no longer triggers a network refresh (it just reloads
    // the persisted snapshot), so there is no separate app-open path here.
    override suspend fun refreshCurrentState(): CurrentQuotaState {
        val account = currentAccountStore.currentAccount()
        if (account != null) {
            refreshCoordinator.refresh(account = account, trigger = RefreshTrigger.Manual)
        }
        return currentQuotaStateLoader.loadCurrentState()
    }
}
