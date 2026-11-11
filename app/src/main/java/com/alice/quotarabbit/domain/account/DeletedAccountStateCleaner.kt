package com.alice.quotarabbit.domain.account

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.account.ProviderCurrentSelection
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.QuotaSnapshot

data class AccountListResult(
    val accounts: List<ProviderAccount>,
    val currentAccountId: LocalAccountId?,
    val latestQuotaSnapshots: Map<LocalAccountId, QuotaSnapshot> = emptyMap(),
    val providerCurrentSelections: List<ProviderCurrentSelection> = emptyList(),
)

fun interface AccountListUseCase {
    suspend fun loadAccounts(): AccountListResult
}

object NoopAccountListUseCase : AccountListUseCase {
    override suspend fun loadAccounts(): AccountListResult =
        AccountListResult(accounts = emptyList(), currentAccountId = null)
}

fun interface AccountDeleteUseCase {
    suspend fun deleteAccount(providerId: ProviderId, localAccountId: LocalAccountId)
}

object NoopAccountDeleteUseCase : AccountDeleteUseCase {
    override suspend fun deleteAccount(providerId: ProviderId, localAccountId: LocalAccountId) = Unit
}

fun interface AccountSwitchUseCase {
    suspend fun switchCurrentAccount(providerId: ProviderId, localAccountId: LocalAccountId): Boolean
}

object NoopAccountSwitchUseCase : AccountSwitchUseCase {
    override suspend fun switchCurrentAccount(providerId: ProviderId, localAccountId: LocalAccountId): Boolean = false
}

fun interface AccountRenameUseCase {
    suspend fun renameAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        displayName: String,
    ): ProviderAccount?
}

object NoopAccountRenameUseCase : AccountRenameUseCase {
    override suspend fun renameAccount(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
        displayName: String,
    ): ProviderAccount? = null
}

fun interface AccountSwitchRefreshRequester {
    suspend fun requestAccountSwitchRefresh(account: ProviderAccount)
}

object NoopAccountSwitchRefreshRequester : AccountSwitchRefreshRequester {
    override suspend fun requestAccountSwitchRefresh(account: ProviderAccount) = Unit
}

fun interface CurrentAccountStateRepublisher {
    suspend fun republishCurrentAccountState(account: ProviderAccount)
}

object NoopCurrentAccountStateRepublisher : CurrentAccountStateRepublisher {
    override suspend fun republishCurrentAccountState(account: ProviderAccount) = Unit
}

interface DeletedAccountStateCleaner {
    suspend fun clearDeletedAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): DeletedAccountStateCleanup
}

fun interface DeletedAccountStateCleanup {
    suspend fun restore()
}

object NoopDeletedAccountStateCleaner : DeletedAccountStateCleaner {
    override suspend fun clearDeletedAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): DeletedAccountStateCleanup = DeletedAccountStateCleanup {}
}
