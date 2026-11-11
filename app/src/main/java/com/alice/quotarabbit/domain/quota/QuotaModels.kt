package com.alice.quotarabbit.domain.quota

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.model.SnapshotId
import java.time.Instant

enum class QuotaWindowAvailability { Available, Depleted, Missing, DecodeFailed, Unsupported }

enum class QuotaSnapshotSource {
    DeviceCodeLogin,
    // Legacy values are kept so older local history can still be decoded.
    OAuthWebView,
    AuthJsonImport,
    BackgroundRefresh,
    ManualRefresh,
    WidgetRefresh,
    AppOpenRefresh,
    ApiKeyImport,
    CookieAuth,
    OAuthPkceLogin,
}

enum class QuotaWindowDisplayKind {
    Percent,
    Balance,
    UsageCount,
    MultiModelFraction,
}

data class QuotaModelBucket(
    val modelId: String,
    val displayName: String,
    val remainingFraction: Double,
    val resetAt: Instant?,
)

data class QuotaWindow(
    val windowId: QuotaWindowId,
    val titleKey: String,
    val usedPercent: Int?,
    val resetAt: Instant?,
    val limitWindowSeconds: Int?,
    val isPrimaryCandidate: Boolean,
    val availability: QuotaWindowAvailability,
    val displayKind: QuotaWindowDisplayKind = QuotaWindowDisplayKind.Percent,
    val balanceAmount: String? = null,
    val balanceCurrency: String? = null,
    // Pre-conversion balance, set only when a currency conversion was applied (presentation-only, never persisted).
    val originalBalanceAmount: String? = null,
    val originalBalanceCurrency: String? = null,
    // Optional credit breakdown (e.g. DeepSeek granted vs topped-up), persisted from the provider snapshot.
    val grantedBalance: String? = null,
    val toppedUpBalance: String? = null,
    val usedCount: Int? = null,
    val limitCount: Int? = null,
    val subLabel: String? = null,
    val modelBuckets: List<QuotaModelBucket> = emptyList(),
    // When true, the home usage chart aggregates this window's per-hour usage by summing
    // per-model used-fraction deltas instead of diffing usedPercent. Set by providers whose
    // usedPercent is not a usage total (e.g. Antigravity reports the worst single model).
    val usesModelBucketSum: Boolean = false,
) {
    val remainingPercent: Int? = usedPercent?.let { 100 - it.coerceIn(0, 100) }
    val displayPercent: Int? = remainingPercent
}

/** Provider-reported Codex benefits; null means unknown, never inferred from quota resets. */
data class CodexBenefits(
    val bankedResetCount: Int? = null,
    /** Explicit entitlement renews_at, when known. */
    val subscriptionRenewsAt: Instant? = null,
    /** Expiration/coverage end from entitlement expires_at or an account-matched JWT claim. */
    val subscriptionActiveUntil: Instant? = null,
    val activeUntilFromToken: Boolean = false,
)

data class QuotaSnapshot(
    val snapshotId: SnapshotId,
    val providerId: ProviderId,
    val localAccountId: LocalAccountId,
    val providerAccountId: ProviderAccountId?,
    val fetchedAt: Instant,
    val source: QuotaSnapshotSource,
    val planType: String?,
    val windows: List<QuotaWindow>,
    val credits: Credits?,
    val responseDigest: String?,
    val codexBenefits: CodexBenefits? = null,
)

data class Credits(
    val hasCredits: Boolean,
    val unlimited: Boolean,
    val balance: Double?,
)

internal fun QuotaWindow.canAlert(): Boolean = when {
    availability != QuotaWindowAvailability.Available -> false
    displayKind == QuotaWindowDisplayKind.Balance -> {
        val amount = balanceAmount?.toDoubleOrNull() ?: 0.0
        amount > 0.0
    }
    displayKind == QuotaWindowDisplayKind.UsageCount -> {
        usedCount != null && limitCount != null && usedCount > 0
    }
    else -> usedPercent in 0..100 && resetAt != null  // Percent, MultiModelFraction
}
