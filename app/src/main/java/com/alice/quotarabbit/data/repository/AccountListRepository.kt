package com.alice.quotarabbit.data.repository

import com.alice.quotarabbit.data.local.dao.ProviderAccountDao
import com.alice.quotarabbit.data.local.dao.QuotaSnapshotDao
import com.alice.quotarabbit.data.preferences.CurrentAccountReader
import com.alice.quotarabbit.domain.account.AccountListResult
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.model.ProviderId

class AccountListRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val currentAccountReader: CurrentAccountReader,
) : AccountListUseCase {
    override suspend fun loadAccounts(): AccountListResult {
        val rawAccounts = providerAccountDao.listAll().map { it.toDomain() }
        val currentSelection = currentAccountReader.currentAccountSelection()
            ?.takeIf { selection -> rawAccounts.any { it.localAccountId == selection.localAccountId } }

        val latestQuotaSnapshots = rawAccounts.mapNotNull { account ->
            quotaSnapshotDao.getLatestForAccount(
                providerId = account.providerId.value,
                localAccountId = account.localAccountId.value,
            )?.toDomain()?.let { account.localAccountId to it }
        }.toMap()

        // The refresh pipeline persists snapshots, not the account's last-refresh timestamp, so derive
        // it from the latest snapshot's `fetchedAt` when the account row hasn't recorded one. Mirrors
        // the COALESCE fallback in ProviderAccountDao.listByProvider so newly-added providers don't
        // show "No successful refresh yet" despite having fresh quota.
        val accounts = rawAccounts.map { account ->
            if (account.lastSuccessfulRefreshAt != null) {
                account
            } else {
                latestQuotaSnapshots[account.localAccountId]
                    ?.let { account.copy(lastSuccessfulRefreshAt = it.fetchedAt) }
                    ?: account
            }
        }

        return AccountListResult(
            accounts = accounts,
            currentAccountId = currentSelection?.localAccountId,
            latestQuotaSnapshots = latestQuotaSnapshots,
            providerCurrentSelections = currentAccountReader.currentProviderSelections().filter { selected ->
                accounts.any { it.providerId == selected.providerId && it.localAccountId == selected.localAccountId }
            },
        )
    }
}
