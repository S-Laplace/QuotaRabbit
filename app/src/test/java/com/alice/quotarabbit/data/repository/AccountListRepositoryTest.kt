package com.alice.quotarabbit.data.repository

import androidx.room.Room
import com.alice.quotarabbit.data.local.db.QuotaRabbitDatabase
import com.alice.quotarabbit.data.local.entity.ProviderAccountEntity
import com.alice.quotarabbit.data.preferences.CurrentAccountReader
import com.alice.quotarabbit.data.preferences.CurrentAccountSelection
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.model.SnapshotId
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import java.time.Instant
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
class AccountListRepositoryTest {
    @Test
    fun `loads latest quota snapshots for account list rows`() = runTest {
        withDatabase { db ->
            db.providerAccountDao().upsert(account("local-1"))
            db.quotaSnapshotDao().insert(
                snapshot(
                    localAccountId = "local-1",
                    fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
                    fiveHourUsedPercent = 62,
                    weeklyUsedPercent = 41,
                ).toEntity(),
            )
            db.quotaSnapshotDao().insert(
                snapshot(
                    localAccountId = "local-1",
                    fetchedAt = Instant.parse("2026-05-23T11:00:00Z"),
                    fiveHourUsedPercent = 70,
                    weeklyUsedPercent = 42,
                ).toEntity(),
            )
            val repository = AccountListRepository(
                providerAccountDao = db.providerAccountDao(),
                quotaSnapshotDao = db.quotaSnapshotDao(),
                currentAccountReader = StaticCurrentAccountReader(
                    CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1")),
                ),
            )

            val result = repository.loadAccounts()

            val latest = result.latestQuotaSnapshots.getValue(LocalAccountId("local-1"))
            assertEquals(Instant.parse("2026-05-23T11:00:00Z"), latest.fetchedAt)
            assertEquals(70, latest.windows.first { it.windowId.value == "five_hour" }.usedPercent)
        }
    }

    @Test
    fun `derives last successful refresh from latest snapshot when account row has none`() = runTest {
        withDatabase { db ->
            // Account row has last_successful_refresh_at = null (the refresh pipeline only saves snapshots).
            db.providerAccountDao().upsert(account("local-1"))
            db.quotaSnapshotDao().insert(
                snapshot(
                    localAccountId = "local-1",
                    fetchedAt = Instant.parse("2026-05-23T11:00:00Z"),
                    fiveHourUsedPercent = 70,
                    weeklyUsedPercent = 42,
                ).toEntity(),
            )
            val repository = AccountListRepository(
                providerAccountDao = db.providerAccountDao(),
                quotaSnapshotDao = db.quotaSnapshotDao(),
                currentAccountReader = StaticCurrentAccountReader(
                    CurrentAccountSelection(ProviderId("codex"), LocalAccountId("local-1")),
                ),
            )

            val account = repository.loadAccounts().accounts.single()

            assertEquals(Instant.parse("2026-05-23T11:00:00Z"), account.lastSuccessfulRefreshAt)
        }
    }

    private suspend fun withDatabase(block: suspend (QuotaRabbitDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuotaRabbitDatabase::class.java,
        ).build()
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    private class StaticCurrentAccountReader(
        private val selection: CurrentAccountSelection?,
    ) : CurrentAccountReader {
        override suspend fun currentAccountSelection(): CurrentAccountSelection? = selection
    }

    private fun account(localAccountId: String): ProviderAccountEntity =
        ProviderAccountEntity(
            localAccountId = localAccountId,
            providerId = "codex",
            providerAccountId = "provider-$localAccountId",
            displayName = localAccountId,
            avatarInitial = localAccountId.first().uppercase(),
            avatarColorKey = localAccountId,
            status = "active",
            createdAt = CREATED_AT.toEpochMilli(),
            updatedAt = CREATED_AT.toEpochMilli(),
            lastSuccessfulRefreshAt = null,
        )

    private fun snapshot(
        localAccountId: String,
        fetchedAt: Instant,
        fiveHourUsedPercent: Int,
        weeklyUsedPercent: Int,
    ): QuotaSnapshot =
        QuotaSnapshot(
            snapshotId = SnapshotId("snapshot-$localAccountId-${fetchedAt.toEpochMilli()}"),
            providerId = ProviderId("codex"),
            localAccountId = LocalAccountId(localAccountId),
            providerAccountId = ProviderAccountId("provider-$localAccountId"),
            fetchedAt = fetchedAt,
            source = QuotaSnapshotSource.ManualRefresh,
            planType = "plus",
            windows = listOf(
                quotaWindow("five_hour", fiveHourUsedPercent),
                quotaWindow("weekly", weeklyUsedPercent),
            ),
            credits = null,
            responseDigest = "safe-digest",
        )

    private fun quotaWindow(windowId: String, usedPercent: Int): QuotaWindow =
        QuotaWindow(
            windowId = QuotaWindowId(windowId),
            titleKey = "quota_window_$windowId",
            usedPercent = usedPercent,
            resetAt = Instant.parse("2026-05-23T12:00:00Z"),
            limitWindowSeconds = 18_000,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
        )

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-05-22T00:00:00Z")
    }
}
