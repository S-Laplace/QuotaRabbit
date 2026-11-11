package com.alice.quotarabbit.data.repository

import com.alice.quotarabbit.data.local.dao.ProviderAccountDao
import com.alice.quotarabbit.data.preferences.CurrentAccountSelection
import com.alice.quotarabbit.data.preferences.CurrentAccountStore
import com.alice.quotarabbit.domain.account.AccountRenameUseCase
import com.alice.quotarabbit.domain.account.AccountEditUseCase
import com.alice.quotarabbit.domain.account.AccountEditInput
import com.alice.quotarabbit.domain.account.AccountSwitchUseCase
import com.alice.quotarabbit.domain.account.AccountSwitchRefreshRequester
import com.alice.quotarabbit.domain.account.CurrentAccountStateRepublisher
import com.alice.quotarabbit.domain.account.NoopAccountSwitchRefreshRequester
import com.alice.quotarabbit.domain.account.NoopCurrentAccountStateRepublisher
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import java.time.Clock
import kotlinx.coroutines.CancellationException

class AccountMutationRepository(
    private val providerAccountDao: ProviderAccountDao,
    private val currentAccountStore: CurrentAccountStore,
    private val accountSwitchRefreshRequester: AccountSwitchRefreshRequester = NoopAccountSwitchRefreshRequester,
    private val currentAccountStateRepublisher: CurrentAccountStateRepublisher = NoopCurrentAccountStateRepublisher,
    private val clock: Clock,
) : AccountSwitchUseCase, AccountRenameUseCase, AccountEditUseCase {
    override suspend fun switchCurrentAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): Boolean {
        val account = providerAccountDao.getById(localAccountId.value)
            ?.takeIf { it.providerId == providerId.value }
            ?.toDomain()
            ?: return false

        currentAccountStore.updateCurrentAccountSelection(
            CurrentAccountSelection(
                providerId = account.providerId,
                localAccountId = account.localAccountId,
            ),
        )
        runPostMutationEffect {
            accountSwitchRefreshRequester.requestAccountSwitchRefresh(account)
        }
        return true
    }

    override suspend fun renameAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        displayName: String,
    ): ProviderAccount? {
        val existing = providerAccountDao.getById(localAccountId.value)
            ?.takeIf { it.providerId == providerId.value }
            ?: return null
        val renamed = existing.toDomain().renamedTo(displayName = displayName, updatedAt = clock.instant())
        val updatedRows = providerAccountDao.updateDisplayName(
            providerId = renamed.providerId.value,
            localAccountId = renamed.localAccountId.value,
            displayName = renamed.displayName,
            avatarInitial = renamed.avatarInitial,
            updatedAt = renamed.updatedAt.toEpochMilli(),
        )
        if (updatedRows == 0) return null

        val renamedAccount = providerAccountDao.getById(localAccountId.value)?.toDomain()
            ?: return null
        val currentSelection = currentAccountStore.currentAccountSelection()
        if (currentSelection?.providerId == providerId && currentSelection.localAccountId == localAccountId) {
            runPostMutationEffect {
                currentAccountStateRepublisher.republishCurrentAccountState(renamedAccount)
            }
        }

        return renamedAccount
    }

    override suspend fun editAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        input: AccountEditInput,
    ): ProviderAccount? {
        val safe = input.validated()
        val old = providerAccountDao.getById(localAccountId.value)
            ?.takeIf { it.providerId == providerId.value } ?: return null
        val renamed = old.toDomain().renamedTo(safe.nickname, clock.instant())
        if (providerAccountDao.updateAccountPresentation(
                providerId = providerId.value,
                localAccountId = localAccountId.value,
                displayName = renamed.displayName,
                avatarInitial = renamed.avatarInitial,
                accentColorId = safe.accentColorId,
                manualRenewalDate = safe.manualRenewalDate,
                updatedAt = renamed.updatedAt.toEpochMilli(),
            ) != 1
        ) return null
        val saved = providerAccountDao.getById(localAccountId.value)?.toDomain() ?: return null
        val current = currentAccountStore.currentAccountSelection()
        if (current?.providerId == providerId && current.localAccountId == localAccountId) {
            runPostMutationEffect { currentAccountStateRepublisher.republishCurrentAccountState(saved) }
        }
        return saved
    }

    private suspend fun runPostMutationEffect(effect: suspend () -> Unit) {
        try {
            effect()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Persistence already succeeded; widget/refresh republish failures are recovered by
            // the next app open or refresh and must not roll back the user-visible mutation.
        }
    }
}
