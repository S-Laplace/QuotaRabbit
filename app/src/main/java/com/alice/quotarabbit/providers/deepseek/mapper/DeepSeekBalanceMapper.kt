package com.alice.quotarabbit.providers.deepseek.mapper

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.model.SnapshotId
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.providers.deepseek.dto.DeepSeekBalanceResponseDto
import java.time.Instant

object DeepSeekBalanceMapper {
    fun map(
        dto: DeepSeekBalanceResponseDto,
        localAccountId: LocalAccountId,
        providerAccountId: ProviderAccountId?,
        fetchedAt: Instant,
        source: QuotaSnapshotSource,
    ): QuotaSnapshot {
        // A successful balance fetch always yields a balance window. Even when balance_infos is empty
        // (fresh / zero-balance account) we surface a zero window so the imported account never reads
        // as "No quota yet" on Home.
        val balanceInfo = dto.balance_infos.firstOrNull()
        val window = QuotaWindow(
            windowId = QuotaWindowId("balance"),
            titleKey = "deepseek_balance",
            usedPercent = null,
            resetAt = null,
            limitWindowSeconds = null,
            isPrimaryCandidate = true,
            availability = if (dto.is_available) QuotaWindowAvailability.Available
                else QuotaWindowAvailability.Depleted,
            displayKind = QuotaWindowDisplayKind.Balance,
            balanceAmount = balanceInfo?.total_balance ?: "0",
            balanceCurrency = balanceInfo?.currency,
            subLabel = null,
            grantedBalance = balanceInfo?.granted_balance,
            toppedUpBalance = balanceInfo?.topped_up_balance,
        )
        return QuotaSnapshot(
            snapshotId = SnapshotId("ds_${fetchedAt}"),
            providerId = DEEPSEEK_PROVIDER_ID,
            localAccountId = localAccountId,
            providerAccountId = providerAccountId,
            fetchedAt = fetchedAt,
            source = source,
            planType = null,
            windows = listOf(window),
            credits = null,
            responseDigest = null,
        )
    }

    private val DEEPSEEK_PROVIDER_ID = ProviderId("deepseek")
}
