package com.alice.quotarabbit.data.repository

import androidx.room.withTransaction
import com.alice.quotarabbit.data.local.db.QuotaRabbitDatabase
import com.alice.quotarabbit.data.preferences.CurrentAccountSelection
import com.alice.quotarabbit.data.preferences.CurrentAccountStore
import com.alice.quotarabbit.data.secure.SecureSessionStore
import com.alice.quotarabbit.domain.account.DeletedAccountStateCleanup
import com.alice.quotarabbit.domain.account.DeletedAccountStateCleaner
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId

class AccountDeletionRepository(
    private val database: QuotaRabbitDatabase,
    private val secureSessionStore: SecureSessionStore,
    private val currentAccountStore: CurrentAccountStore,
    private val deletedAccountStateCleaner: DeletedAccountStateCleaner,
    private val localDataDeleter: AccountLocalDataDeleter = RoomAccountLocalDataDeleter(database),
) {
    suspend fun deleteAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ) {
        val providerValue = providerId.value
        val localValue = localAccountId.value
        val previousSelection = currentAccountStore.currentAccountSelection()
        val nextSelection = nextCurrentSelectionAfterDeleting(
            providerId = providerId,
            deletedLocalAccountId = localAccountId,
            currentSelection = previousSelection,
        )
        val shouldUpdateCurrentSelection = previousSelection?.providerId == providerId &&
            previousSelection.localAccountId == localAccountId
        val sessionBeforeDelete = secureSessionStore.load(
            providerId = providerValue,
            localAccountId = localValue,
        )

        var deletedAccountStateCleanup: DeletedAccountStateCleanup? = null

        try {
            if (shouldUpdateCurrentSelection) {
                currentAccountStore.updateCurrentAccountSelection(nextSelection)
            }
            deletedAccountStateCleanup = deletedAccountStateCleaner.clearDeletedAccountState(
                providerId = providerId,
                localAccountId = localAccountId,
            )
            secureSessionStore.delete(
                providerId = providerValue,
                localAccountId = localValue,
            )
            localDataDeleter.deleteLocalData(providerId = providerId, localAccountId = localAccountId)
            currentAccountStore.clearProviderSelectionIfMatches(CurrentAccountSelection(providerId, localAccountId))
        } catch (throwable: Throwable) {
            deletedAccountStateCleanup?.let { cleanup ->
                runCatching { cleanup.restore() }
            }
            if (sessionBeforeDelete != null) {
                runCatching { secureSessionStore.save(sessionBeforeDelete) }
            }
            if (shouldUpdateCurrentSelection) {
                runCatching { currentAccountStore.updateCurrentAccountSelection(previousSelection) }
            }
            throw throwable
        }
    }

    private suspend fun nextCurrentSelectionAfterDeleting(
        providerId: ProviderId,
        deletedLocalAccountId: LocalAccountId,
        currentSelection: CurrentAccountSelection?,
    ): CurrentAccountSelection? {
        if (currentSelection?.providerId != providerId ||
            currentSelection.localAccountId != deletedLocalAccountId
        ) {
            return currentSelection
        }

        return database.providerAccountDao()
            .listByProvider(providerId.value)
            .filterNot { it.localAccountId == deletedLocalAccountId.value }
            .firstOrNull()
            ?.let { account ->
                CurrentAccountSelection(
                    providerId = providerId,
                    localAccountId = LocalAccountId(account.localAccountId),
                )
            }
    }
}

interface AccountLocalDataDeleter {
    suspend fun deleteLocalData(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    )
}

class RoomAccountLocalDataDeleter(
    private val database: QuotaRabbitDatabase,
) : AccountLocalDataDeleter {
    override suspend fun deleteLocalData(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ) {
        val providerValue = providerId.value
        val localValue = localAccountId.value
        database.withTransaction {
            val account = database.providerAccountDao().getById(localValue)
                ?.takeIf { it.providerId == providerValue }

            database.quotaSnapshotDao().deleteForAccount(
                providerId = providerValue,
                localAccountId = localValue,
            )
            database.refreshAttemptDao().deleteForAccount(
                providerId = providerValue,
                localAccountId = localValue,
            )
            database.alertStateDao().deleteForAccount(
                providerId = providerValue,
                localAccountId = localValue,
            )
            if (account != null) {
                database.providerAccountDao().delete(account)
            }
        }
    }
}
