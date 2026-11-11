package com.alice.quotarabbit.refresh

import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.refresh.QuotaError
import com.alice.quotarabbit.domain.refresh.RefreshTrigger

class CompositeRefreshProvider(
    private val providers: Map<ProviderId, RefreshProvider>,
) : RefreshProvider {
    override suspend fun refresh(
        account: ProviderAccount,
        trigger: RefreshTrigger,
    ): ProviderRefreshResult =
        providers[account.providerId]
            ?.refresh(account, trigger)
            ?: ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "composite_refresh_no_provider_for_${account.providerId.value}",
                ),
            )
}
