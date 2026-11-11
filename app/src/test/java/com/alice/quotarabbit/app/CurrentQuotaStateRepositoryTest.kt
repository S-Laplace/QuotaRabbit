package com.alice.quotarabbit.app

import androidx.room.Room
import com.alice.quotarabbit.data.local.db.QuotaRabbitDatabase
import com.alice.quotarabbit.data.local.entity.ProviderAccountEntity
import com.alice.quotarabbit.data.local.entity.QuotaSnapshotEntity
import com.alice.quotarabbit.data.preferences.CurrentAccountReader
import com.alice.quotarabbit.data.preferences.CurrentAccountSelection
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.quota.CurrentQuotaStatus
import com.alice.quotarabbit.domain.settings.NotificationAccountSelection
import com.alice.quotarabbit.domain.settings.NotificationPreferenceReader
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.domain.settings.PrimaryQuotaWindowPreferenceReader
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric SDK 36 currently requires Java 21, while this project is pinned to Java 17.
@Config(sdk = [35])
class CurrentQuotaStateRepositoryTest {
    @Test
    fun `dashboard enumerates three cached codex accounts and mixed providers in stable groups`() = runTest {
        withRepository { db, reader, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "c-3"))
            db.providerAccountDao().upsert(account(localAccountId = "c-1"))
            db.providerAccountDao().upsert(account(localAccountId = "c-2", status = "needs_reauth"))
            db.providerAccountDao().upsert(account(localAccountId = "ds").copy(providerId = "deepseek"))
            db.providerAccountDao().upsert(account(localAccountId = "ag", status = "disabled").copy(providerId = "antigravity"))
            db.providerAccountDao().upsert(account(localAccountId = "gone", status = "deleted"))
            for (id in listOf("c-1", "c-2", "c-3")) {
                db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-$id", localAccountId = id))
            }
            reader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("c-2"))

            val dashboard = repository.loadDashboardState()

            assertEquals(listOf("c-1", "c-2", "c-3", "ag", "ds"), dashboard.accountStates.map { it.account?.localAccountId?.value })
            assertEquals("c-2", dashboard.currentState.account?.localAccountId?.value)
            assertEquals(CurrentQuotaStatus.AuthRequired, dashboard.accountStates[1].status)
            assertEquals(listOf(62, 62, 62), dashboard.accountStates.take(3).map { it.primaryWindow?.usedPercent })
            assertEquals(CurrentQuotaStatus.NoData, dashboard.accountStates.last().status)
        }
    }

    @Test
    fun `dashboard orders each provider by creation time then stable ID independent of labels or selection`() = runTest {
        withRepository { db, reader, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "new", displayName = "A"))
            db.providerAccountDao().upsert(account(localAccountId = "old-b", displayName = "B").copy(createdAt = 1))
            db.providerAccountDao().upsert(account(localAccountId = "old-a", displayName = "Z").copy(createdAt = 1))
            reader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("new"))
            assertEquals(listOf("old-a", "old-b", "new"), repository.loadDashboardState().accountStates.map { it.account?.localAccountId?.value })
        }
    }

    @Test
    fun `missing global selection still displays saved accounts without changing selection`() = runTest {
        withRepository { db, reader, repository ->
            db.providerAccountDao().upsert(account())
            db.quotaSnapshotDao().insert(snapshot())
            reader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("missing"))

            val dashboard = repository.loadDashboardState()

            assertEquals(CurrentQuotaStatus.Unauthenticated, dashboard.currentState.status)
            assertEquals(1, dashboard.accountStates.size)
            assertEquals("missing", reader.selection?.localAccountId?.value)
        }
    }

    @Test
    fun `no current account loads unauthenticated state`() = runTest {
        withRepository { _, _, repository ->
            val state = repository.loadCurrentState()

            assertEquals(CurrentQuotaStatus.Unauthenticated, state.status)
        }
    }

    @Test
    fun `current account loads latest successful snapshot`() = runTest {
        withRepository { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(status = "active"))
            db.quotaSnapshotDao().insert(snapshot(fetchedAt = Instant.parse("2026-05-23T11:50:00Z")))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadCurrentState()

            assertEquals(CurrentQuotaStatus.Fresh, state.status)
            assertEquals("Work", state.account?.displayName)
            assertEquals(62, state.primaryWindow?.usedPercent)
        }
    }

    @Test
    fun `current account uses persisted primary quota window`() = runTest {
        withRepository(primaryWindowId = QuotaWindowId("weekly")) { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(status = "active"))
            db.quotaSnapshotDao().insert(snapshot(fetchedAt = Instant.parse("2026-05-23T11:50:00Z")))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadCurrentState()

            assertEquals(QuotaWindowId("weekly"), state.primaryWindow?.windowId)
            assertEquals(41, state.primaryWindow?.usedPercent)
            assertEquals(listOf(QuotaWindowId("five_hour")), state.secondaryWindows.map { it.windowId })
        }
    }

    @Test
    fun `needs reauth current account loads auth required state`() = runTest {
        withRepository { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(status = "needs_reauth"))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadCurrentState()

            assertEquals(CurrentQuotaStatus.AuthRequired, state.status)
        }
    }

    @Test
    fun `status notification state uses configured account and window`() = runTest {
        withRepository(
            notificationPreferences = NotificationPreferences(
                persistentNotificationAccount = NotificationAccountSelection(
                    providerId = ProviderId("codex"),
                    localAccountId = LocalAccountId("local-2"),
                ),
                persistentNotificationWindowId = QuotaWindowId("weekly"),
            ),
        ) { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "local-1", displayName = "Current"))
            db.providerAccountDao().upsert(account(localAccountId = "local-2", displayName = "Configured"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-1", localAccountId = "local-1"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-2", localAccountId = "local-2"))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadStatusNotificationState(refreshedState = repository.loadCurrentState())

            assertEquals("Configured", state.account?.displayName)
            assertEquals(QuotaWindowId("weekly"), state.primaryWindow?.windowId)
            assertEquals(41, state.primaryWindow?.usedPercent)
        }
    }

    @Test
    fun `requested account state uses supplied account and window`() = runTest {
        withRepository { db, currentAccountReader, repository ->
            db.providerAccountDao().upsert(account(localAccountId = "local-1", displayName = "Current"))
            db.providerAccountDao().upsert(account(localAccountId = "local-2", displayName = "Requested"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-1", localAccountId = "local-1"))
            db.quotaSnapshotDao().insert(snapshot(snapshotId = "snapshot-2", localAccountId = "local-2"))
            currentAccountReader.selection = CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1"))

            val state = repository.loadAccountState(
                providerId = ProviderId("codex"),
                localAccountId = LocalAccountId("local-2"),
                primaryWindowId = QuotaWindowId("weekly"),
            )

            assertEquals("Requested", state.account?.displayName)
            assertEquals(QuotaWindowId("weekly"), state.primaryWindow?.windowId)
            assertEquals(41, state.primaryWindow?.usedPercent)
        }
    }

    private suspend fun withRepository(
        primaryWindowId: QuotaWindowId = QuotaWindowId("five_hour"),
        notificationPreferences: NotificationPreferences = NotificationPreferences(),
        block: suspend (QuotaRabbitDatabase, InMemoryCurrentAccountReader, CurrentQuotaStateRepository) -> Unit,
    ) {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuotaRabbitDatabase::class.java,
        ).build()
        val currentAccountReader = InMemoryCurrentAccountReader()
        val repository = CurrentQuotaStateRepository(
            currentAccountReader = currentAccountReader,
            providerAccountDao = db.providerAccountDao(),
            quotaSnapshotDao = db.quotaSnapshotDao(),
            refreshAttemptDao = db.refreshAttemptDao(),
            primaryQuotaWindowPreferenceReader = StaticPrimaryQuotaWindowPreferenceReader(primaryWindowId),
            notificationPreferenceReader = StaticNotificationPreferenceReader(notificationPreferences),
            clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC),
        )

        try {
            block(db, currentAccountReader, repository)
        } finally {
            db.close()
        }
    }

    private class InMemoryCurrentAccountReader : CurrentAccountReader {
        var selection: CurrentAccountSelection? = null

        override suspend fun currentAccountSelection(): CurrentAccountSelection? = selection
    }

    private class StaticPrimaryQuotaWindowPreferenceReader(
        private val windowId: QuotaWindowId,
    ) : PrimaryQuotaWindowPreferenceReader {
        override suspend fun primaryQuotaWindowId(): QuotaWindowId = windowId
    }

    private class StaticNotificationPreferenceReader(
        private val notificationPreferences: NotificationPreferences,
    ) : NotificationPreferenceReader {
        override suspend fun notificationPreferences(): NotificationPreferences = notificationPreferences
    }

    private fun account(
        status: String = "active",
        localAccountId: String = "local-1",
        displayName: String = "Work",
    ) = ProviderAccountEntity(
        localAccountId = localAccountId,
        providerId = "codex",
        providerAccountId = "acct-$localAccountId",
        displayName = displayName,
        avatarInitial = displayName.first().uppercase(),
        avatarColorKey = localAccountId,
        status = status,
        createdAt = Instant.parse("2026-05-23T11:00:00Z").toEpochMilli(),
        updatedAt = Instant.parse("2026-05-23T11:30:00Z").toEpochMilli(),
        lastSuccessfulRefreshAt = null,
    )

    private fun snapshot(
        fetchedAt: Instant = Instant.parse("2026-05-23T11:50:00Z"),
        snapshotId: String = "snapshot-1",
        localAccountId: String = "local-1",
    ) = QuotaSnapshotEntity(
        snapshotId = snapshotId,
        providerId = "codex",
        localAccountId = localAccountId,
        providerAccountId = "acct-$localAccountId",
        fetchedAt = fetchedAt.toEpochMilli(),
        source = "manualRefresh",
        planType = "plus",
        windowsJson = """[{"windowId":"five_hour","titleKey":"quota_window_five_hour","usedPercent":62,"resetAt":1779555600000,"limitWindowSeconds":18000,"isPrimaryCandidate":true,"availability":"Available"},{"windowId":"weekly","titleKey":"quota_window_weekly","usedPercent":41,"resetAt":1780012800000,"limitWindowSeconds":604800,"isPrimaryCandidate":true,"availability":"Available"}]""",
        creditsJson = null,
        responseDigest = "safe-digest",
    )
}
