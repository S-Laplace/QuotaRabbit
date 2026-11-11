package com.alice.quotarabbit.app

import com.alice.quotarabbit.data.local.dao.ProviderAccountDao
import com.alice.quotarabbit.data.local.dao.QuotaSnapshotDao
import com.alice.quotarabbit.data.local.dao.RefreshAttemptDao
import com.alice.quotarabbit.data.preferences.CurrentAccountReader
import com.alice.quotarabbit.data.repository.toDomain
import com.alice.quotarabbit.domain.account.CurrentAccountStateRepublisher
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.quota.CurrentQuotaStateFactory
import com.alice.quotarabbit.domain.settings.DefaultPrimaryQuotaWindowPreferenceReader
import com.alice.quotarabbit.domain.settings.PrimaryQuotaWindowPreferenceReader
import com.alice.quotarabbit.refresh.CurrentQuotaStatePublisher
import java.time.Clock

internal class CurrentAccountQuotaStateRepublisher(
    private val currentAccountReader: CurrentAccountReader,
    private val providerAccountDao: ProviderAccountDao,
    private val quotaSnapshotDao: QuotaSnapshotDao,
    private val refreshAttemptDao: RefreshAttemptDao,
    private val currentQuotaStatePublisher: CurrentQuotaStatePublisher,
    private val currentQuotaStateFactory: CurrentQuotaStateFactory = CurrentQuotaStateFactory(),
    private val primaryQuotaWindowPreferenceReader: PrimaryQuotaWindowPreferenceReader =
        DefaultPrimaryQuotaWindowPreferenceReader,
    private val clock: Clock,
) : CurrentAccountStateRepublisher {
    override suspend fun republishCurrentAccountState(account: ProviderAccount) {
        val currentSelection = currentAccountReader.currentAccountSelection() ?: return
        if (currentSelection.providerId != account.providerId ||
            currentSelection.localAccountId != account.localAccountId
        ) {
            return
        }

        val persistedAccount = providerAccountDao.getById(account.localAccountId.value)
            ?.toDomain()
            ?.takeIf { it.providerId == account.providerId }
            ?: return
        val latestSnapshot = quotaSnapshotDao.getLatestForAccount(
            providerId = persistedAccount.providerId.value,
            localAccountId = persistedAccount.localAccountId.value,
        )?.toDomain()
        val latestAttempt = refreshAttemptDao.getLatestForAccount(
            providerId = persistedAccount.providerId.value,
            localAccountId = persistedAccount.localAccountId.value,
        )?.toDomain()

        currentQuotaStatePublisher.publish(
            currentQuotaStateFactory.create(
                account = persistedAccount,
                latestSnapshot = latestSnapshot,
                latestAttempt = latestAttempt,
                now = clock.instant(),
                primaryWindowId = primaryQuotaWindowPreferenceReader.primaryQuotaWindowId(),
            ),
        )
    }
}
