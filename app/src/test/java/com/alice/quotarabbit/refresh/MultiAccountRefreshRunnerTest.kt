package com.alice.quotarabbit.refresh

import com.alice.quotarabbit.domain.model.AccountStatus
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.RefreshAttemptId
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.refresh.QuotaError
import com.alice.quotarabbit.domain.refresh.RefreshAttempt
import com.alice.quotarabbit.domain.refresh.RefreshTrigger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiAccountRefreshRunnerTest {
    private val now = Instant.parse("2026-05-23T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `completion callback publishes fast failure before another account finishes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = object : RefreshProvider {
            override suspend fun refresh(account: ProviderAccount, trigger: RefreshTrigger): ProviderRefreshResult {
                if (account.localAccountId.value == "slow") gate.await()
                return ProviderRefreshResult.Failure(QuotaError.Network(null))
            }
        }
        val completed = mutableListOf<String>()
        val runner = MultiAccountRefreshRunner(coordinator(provider), parallelism = 2)
        val job = launch {
            runner.refresh(listOf(account("fast", AccountStatus.Active), account("slow", AccountStatus.Active)),
                RefreshTrigger.Manual, onAccountRefreshed = { account, _ -> completed += account.localAccountId.value })
        }
        runCurrent()
        assertEquals(listOf("fast"), completed)
        gate.complete(Unit)
        job.join()
        assertEquals(listOf("fast", "slow"), completed)
    }

    @Test
    fun `refresh covers needs-reauth accounts passed in for a manual retry`() = runTest {
        // A manual pull-to-refresh hands in Active + NeedsReauth accounts; the runner must refresh
        // every one it is given. Account-state filtering is the caller's job (activeAccounts for
        // background, manuallyRefreshableAccounts for manual), not the runner's.
        val provider = RecordingRefreshProvider()
        val runner = MultiAccountRefreshRunner(refreshCoordinator = coordinator(provider))

        runner.refresh(
            accounts = listOf(
                account("local-active", AccountStatus.Active),
                account("local-reauth", AccountStatus.NeedsReauth),
            ),
            trigger = RefreshTrigger.Manual,
        )

        assertEquals(
            setOf("local-active", "local-reauth"),
            provider.refreshedAccountIds.toSet(),
        )
    }

    private fun coordinator(provider: RefreshProvider): RefreshCoordinator =
        RefreshCoordinator(
            provider = provider,
            snapshotStore = NoopSnapshotStore,
            attemptStore = NoopAttemptStore,
            attemptIdProvider = SequentialAttemptIdProvider(),
            clock = clock,
        )

    private fun account(localAccountId: String, status: AccountStatus): ProviderAccount =
        ProviderAccount.createNew(
            localAccountId = LocalAccountId(localAccountId),
            providerId = ProviderId("codex"),
            providerAccountId = ProviderAccountId("acct-$localAccountId"),
            displayName = localAccountId,
            now = now,
        ).copy(status = status)

    private class RecordingRefreshProvider : RefreshProvider {
        val refreshedAccountIds = mutableListOf<String>()

        override suspend fun refresh(
            account: ProviderAccount,
            trigger: RefreshTrigger,
        ): ProviderRefreshResult {
            refreshedAccountIds += account.localAccountId.value
            return ProviderRefreshResult.Failure(QuotaError.Network(diagnosticsDigest = null))
        }
    }

    private object NoopSnapshotStore : SnapshotStore {
        override suspend fun save(snapshot: QuotaSnapshot) = Unit
        override suspend fun latestFor(account: ProviderAccount): QuotaSnapshot? = null
    }

    private object NoopAttemptStore : RefreshAttemptStore {
        override suspend fun save(attempt: RefreshAttempt) = Unit
    }

    private class SequentialAttemptIdProvider : AttemptIdProvider {
        private var next = 1
        override fun nextId(): RefreshAttemptId = RefreshAttemptId("attempt-${next++}")
    }
}
