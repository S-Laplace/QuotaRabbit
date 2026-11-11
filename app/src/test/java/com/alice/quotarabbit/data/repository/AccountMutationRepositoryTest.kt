package com.alice.quotarabbit.data.repository

import androidx.room.Room
import com.alice.quotarabbit.data.local.db.QuotaRabbitDatabase
import com.alice.quotarabbit.data.local.entity.ProviderAccountEntity
import com.alice.quotarabbit.data.preferences.CurrentAccountSelection
import com.alice.quotarabbit.data.preferences.CurrentAccountStore
import com.alice.quotarabbit.domain.account.CurrentAccountStateRepublisher
import com.alice.quotarabbit.domain.account.AccountSwitchRefreshRequester
import com.alice.quotarabbit.domain.account.AccountEditInput
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class AccountMutationRepositoryTest {
    @Test
    fun `switching existing account persists current selection`() = runTest {
        val refreshRequester = RecordingAccountSwitchRefreshRequester()
        withRepository(
            accountSwitchRefreshRequester = refreshRequester,
        ) { db, currentAccountStore, repository ->
            db.providerAccountDao().upsert(account("local-1"))
            db.providerAccountDao().upsert(account("local-2"))

            val switched = repository.switchCurrentAccount(ProviderId("codex"), LocalAccountId("local-2"))

            assertTrue(switched)
            assertEquals(
                CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-2")),
                currentAccountStore.currentAccountSelection(),
            )
            assertEquals(
                LocalAccountId("local-2"),
                AccountListRepository(
                    providerAccountDao = db.providerAccountDao(),
                    quotaSnapshotDao = db.quotaSnapshotDao(),
                    currentAccountReader = currentAccountStore,
                )
                    .loadAccounts()
                    .currentAccountId,
            )
            assertEquals(listOf(LocalAccountId("local-2")), refreshRequester.requestedAccountIds)
        }
    }

    @Test
    fun `switching missing account does not mutate current selection`() = runTest {
        withRepository { _, currentAccountStore, repository ->
            currentAccountStore.updateCurrentAccountSelection(
                CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1")),
            )

            val switched = repository.switchCurrentAccount(ProviderId("codex"), LocalAccountId("missing"))

            assertFalse(switched)
            assertEquals(
                CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1")),
                currentAccountStore.currentAccountSelection(),
            )
        }
    }

    @Test
    fun `renaming existing account persists display name avatar initial and updated time`() = runTest {
        withRepository { db, _, repository ->
            db.providerAccountDao().upsert(account("local-1", displayName = "Codex Main"))

            val renamed = repository.renameAccount(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("local-1"),
                displayName = "  Work  ",
            )

            val entity = db.providerAccountDao().getById("local-1")
            assertEquals("Work", renamed?.displayName)
            assertEquals("W", renamed?.avatarInitial)
            assertEquals(UPDATED_AT, renamed?.updatedAt)
            assertEquals("Work", entity?.displayName)
            assertEquals("W", entity?.avatarInitial)
            assertEquals(UPDATED_AT.toEpochMilli(), entity?.updatedAt)
            assertEquals("provider-local-1", entity?.providerAccountId)
            assertEquals("active", entity?.status)
            assertEquals(CREATED_AT.toEpochMilli(), entity?.createdAt)
        }
    }

    @Test
    fun `renaming current account republishes current account projection`() = runTest {
        val republisher = RecordingCurrentAccountStateRepublisher()
        withRepository(
            currentAccountStateRepublisher = republisher,
        ) { db, currentAccountStore, repository ->
            db.providerAccountDao().upsert(account("local-1", displayName = "Codex Main"))
            currentAccountStore.updateCurrentAccountSelection(
                CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1")),
            )

            repository.renameAccount(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("local-1"),
                displayName = "Work",
            )

            assertEquals(listOf(LocalAccountId("local-1")), republisher.republishedAccountIds)
            assertEquals("Work", republisher.republishedAccounts.single().displayName)
        }
    }

    @Test
    fun `renaming non-current account does not republish current account projection`() = runTest {
        val republisher = RecordingCurrentAccountStateRepublisher()
        withRepository(
            currentAccountStateRepublisher = republisher,
        ) { db, currentAccountStore, repository ->
            db.providerAccountDao().upsert(account("local-1", displayName = "Codex Main"))
            db.providerAccountDao().upsert(account("local-2", displayName = "Backup"))
            currentAccountStore.updateCurrentAccountSelection(
                CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-2")),
            )

            repository.renameAccount(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("local-1"),
                displayName = "Work",
            )

            assertEquals(emptyList<LocalAccountId>(), republisher.republishedAccountIds)
        }
    }

    @Test
    fun `renaming missing account returns null`() = runTest {
        withRepository { _, _, repository ->
            val renamed = repository.renameAccount(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("missing"),
                displayName = "Work",
            )

            assertNull(renamed)
        }
    }

    @Test
    fun `edit account atomically persists name color date and isolates all other accounts`() = runTest {
        withRepository { db, _, repository ->
            db.providerAccountDao().upsert(account("local-1", displayName = "Original"))
            db.providerAccountDao().upsert(account("local-2", displayName = "Other"))
            val before = db.providerAccountDao().getById("local-1")!!

            val saved = repository.editAccount(
                ProviderId("codex"), LocalAccountId("local-1"),
                AccountEditInput("  Edited  ", "teal", "2027-01-01"),
            )
            assertEquals("Edited", saved?.displayName)
            assertEquals("teal", saved?.accentColorId)
            assertEquals("2027-01-01", saved?.manualRenewalDate)
            val entity = db.providerAccountDao().getById("local-1")!!
            assertEquals(before.localAccountId, entity.localAccountId)
            assertEquals(before.providerAccountId, entity.providerAccountId)
            assertEquals(before.avatarColorKey, entity.avatarColorKey)
            assertEquals(before.createdAt, entity.createdAt)
            assertEquals(before.lastSuccessfulRefreshAt, entity.lastSuccessfulRefreshAt)
            assertEquals("Other", db.providerAccountDao().getById("local-2")!!.displayName)
            assertNull(db.providerAccountDao().getById("local-2")!!.manualRenewalDate)
            val cleared = repository.editAccount(ProviderId("codex"), LocalAccountId("local-1"),
                AccountEditInput("Edited", null, null))
            assertNull(cleared?.accentColorId)
            assertNull(cleared?.manualRenewalDate)
        }
    }

    @Test
    fun `edit account refuses cross-provider id and reauthentication upsert keeps preferences`() = runTest {
        withRepository { db, _, repository ->
            db.providerAccountDao().upsert(account("local-1"))
            assertNull(repository.editAccount(ProviderId("deepseek"), LocalAccountId("local-1"),
                AccountEditInput("Other", "rose", null)))
            repository.editAccount(ProviderId("codex"), LocalAccountId("local-1"),
                AccountEditInput("Label", "rose", "2026-12-31"))
            db.providerAccountDao().upsert(account("local-1").copy(status = "needs_reauth"))
            val stored = db.providerAccountDao().getById("local-1")!!
            assertEquals("Label", stored.displayName)
            assertEquals("rose", stored.accentColorId)
            assertEquals("2026-12-31", stored.manualRenewalDate)
        }
    }

    @Test
    fun `upserting provider without remote account id preserves edited local presentation`() = runTest {
        withRepository { db, _, repository ->
            val dao = db.providerAccountDao()
            val original = account("local-anonymous", "Generated").copy(
                providerId = "deepseek",
                providerAccountId = null,
            )
            dao.upsert(original)
            val edited = repository.editAccount(
                ProviderId("deepseek"), LocalAccountId("local-anonymous"),
                AccountEditInput("My custom name", "violet", "2027-02-28"),
            )
            assertEquals("My custom name", edited?.displayName)
            dao.upsert(original.copy(
                displayName = "Provider changed label",
                avatarInitial = "P",
                updatedAt = UPDATED_AT.toEpochMilli(),
                lastSuccessfulRefreshAt = UPDATED_AT.toEpochMilli(),
            ))
            val saved = requireNotNull(dao.getById("local-anonymous"))
            assertEquals("My custom name", saved.displayName)
            assertEquals("M", saved.avatarInitial)
            assertEquals("violet", saved.accentColorId)
            assertEquals("2027-02-28", saved.manualRenewalDate)
            assertEquals(null, saved.providerAccountId)
            assertEquals(UPDATED_AT.toEpochMilli(), saved.lastSuccessfulRefreshAt)
        }
    }

    private suspend fun withRepository(
        accountSwitchRefreshRequester: AccountSwitchRefreshRequester = RecordingAccountSwitchRefreshRequester(),
        currentAccountStateRepublisher: CurrentAccountStateRepublisher = RecordingCurrentAccountStateRepublisher(),
        block: suspend (QuotaRabbitDatabase, InMemoryCurrentAccountStore, AccountMutationRepository) -> Unit,
    ) {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuotaRabbitDatabase::class.java,
        ).build()
        val currentAccountStore = InMemoryCurrentAccountStore()
        val repository = AccountMutationRepository(
            providerAccountDao = db.providerAccountDao(),
            currentAccountStore = currentAccountStore,
            accountSwitchRefreshRequester = accountSwitchRefreshRequester,
            currentAccountStateRepublisher = currentAccountStateRepublisher,
            clock = Clock.fixed(UPDATED_AT, ZoneOffset.UTC),
        )

        try {
            block(db, currentAccountStore, repository)
        } finally {
            db.close()
        }
    }

    private class RecordingAccountSwitchRefreshRequester : AccountSwitchRefreshRequester {
        val requestedAccounts = mutableListOf<ProviderAccount>()
        val requestedAccountIds: List<LocalAccountId>
            get() = requestedAccounts.map { it.localAccountId }

        override suspend fun requestAccountSwitchRefresh(account: ProviderAccount) {
            requestedAccounts += account
        }
    }

    private class RecordingCurrentAccountStateRepublisher : CurrentAccountStateRepublisher {
        val republishedAccounts = mutableListOf<ProviderAccount>()
        val republishedAccountIds: List<LocalAccountId>
            get() = republishedAccounts.map { it.localAccountId }

        override suspend fun republishCurrentAccountState(account: ProviderAccount) {
            republishedAccounts += account
        }
    }

    private class InMemoryCurrentAccountStore : CurrentAccountStore {
        private var selection: CurrentAccountSelection? = null

        override suspend fun currentAccountSelection(): CurrentAccountSelection? = selection

        override suspend fun updateCurrentAccountSelection(selection: CurrentAccountSelection?) {
            this.selection = selection
        }
    }

    private fun account(localAccountId: String, displayName: String = localAccountId) = ProviderAccountEntity(
        localAccountId = localAccountId,
        providerId = "codex",
        providerAccountId = "provider-$localAccountId",
        displayName = displayName,
        avatarInitial = displayName.first().uppercase(),
        avatarColorKey = localAccountId,
        status = "active",
        createdAt = CREATED_AT.toEpochMilli(),
        updatedAt = CREATED_AT.toEpochMilli(),
        lastSuccessfulRefreshAt = null,
    )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-05-22T00:00:00Z")
        val UPDATED_AT: Instant = Instant.parse("2026-05-24T12:00:00Z")
    }
}
