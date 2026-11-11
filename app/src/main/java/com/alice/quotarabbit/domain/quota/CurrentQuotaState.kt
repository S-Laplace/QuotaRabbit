package com.alice.quotarabbit.domain.quota

import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.refresh.QuotaError
import com.alice.quotarabbit.domain.refresh.RefreshAttempt

enum class CurrentQuotaStatus {
    Unauthenticated,
    Loading,
    Fresh,
    PossiblyStale,
    Expired,
    AuthRequired,
    ErrorWithLastKnownGood,
    NoData,
}

enum class CurrentQuotaFreshness {
    Unknown,
    Fresh,
    PossiblyStale,
    Expired,
}

data class CurrentQuotaState(
    val status: CurrentQuotaStatus,
    val freshness: CurrentQuotaFreshness,
    val account: ProviderAccount?,
    val snapshot: QuotaSnapshot?,
    val latestAttempt: RefreshAttempt?,
    val primaryWindow: QuotaWindow?,
    val secondaryWindows: List<QuotaWindow>,
    val primaryWindowCanAlert: Boolean,
    val error: QuotaError?,
)
