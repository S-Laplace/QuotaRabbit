package com.alice.quotarabbit.domain.quota

import com.alice.quotarabbit.domain.account.ProviderCurrentSelection

/** Saved, display-safe account states; selection only controls the current-account marker and trend. */
data class HomeDashboardState(
    val currentState: CurrentQuotaState,
    val accountStates: List<CurrentQuotaState>,
    val providerCurrentSelections: List<ProviderCurrentSelection> = emptyList(),
)
