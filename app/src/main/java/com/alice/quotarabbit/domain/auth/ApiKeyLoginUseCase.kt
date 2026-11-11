package com.alice.quotarabbit.domain.auth

import com.alice.quotarabbit.data.local.dao.ProviderAccountDao
import com.alice.quotarabbit.data.local.dao.QuotaSnapshotDao
import com.alice.quotarabbit.data.preferences.CurrentAccountPreferences
import com.alice.quotarabbit.data.preferences.CurrentAccountSelection
import com.alice.quotarabbit.data.repository.toEntity
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.providers.SessionImportRouter
import java.time.Clock
import java.util.UUID

class ApiKeyLoginUseCase(
    private val importRouter: SessionImportRouter,
    private val accountDao: ProviderAccountDao,
    private val snapshotDao: QuotaSnapshotDao,
    private val currentAccountStore: CurrentAccountPreferences,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun importApiKey(
        providerId: ProviderId,
        displayName: String,
        apiKey: String,
    ): Result<ProviderAccount> {
        val now = clock.instant()
        val localAccountId = LocalAccountId("${providerId.value}-${UUID.randomUUID()}")
        val account = ProviderAccount.createNew(
            localAccountId = localAccountId,
            providerId = providerId,
            providerAccountId = null,
            displayName = displayName,
            now = now,
        )

        val snapshotResult = importRouter.importApiKey(apiKey, account)
        if (snapshotResult.isFailure) {
            return Result.failure(
                snapshotResult.exceptionOrNull()
                    ?: RuntimeException("Unknown import error"),
            )
        }
        val snapshot = snapshotResult.getOrThrow()

        accountDao.upsert(account.toEntity())
        snapshotDao.insert(snapshot.toEntity())
        currentAccountStore.updateCurrentAccountSelection(
            CurrentAccountSelection(
                providerId = providerId,
                localAccountId = localAccountId,
            ),
        )

        return Result.success(account)
    }
}
