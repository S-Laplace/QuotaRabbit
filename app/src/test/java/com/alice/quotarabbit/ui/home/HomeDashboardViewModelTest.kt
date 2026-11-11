package com.alice.quotarabbit.ui.home

import com.alice.quotarabbit.domain.account.ProviderCurrentSelection
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.model.RefreshAttemptId
import com.alice.quotarabbit.domain.model.SnapshotId
import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.quota.CurrentQuotaStateFactory
import com.alice.quotarabbit.domain.quota.CurrentQuotaStatus
import com.alice.quotarabbit.domain.quota.HomeDashboardState
import com.alice.quotarabbit.domain.quota.QuotaModelBucket
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.domain.refresh.QuotaError
import com.alice.quotarabbit.domain.refresh.RefreshAttempt
import com.alice.quotarabbit.domain.refresh.RefreshAttemptStatus
import com.alice.quotarabbit.domain.refresh.RefreshTrigger
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDashboardViewModelTest {
    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `unreadable or nonfinite balances stay unavailable and never become displayed values`() {
        for (amount in listOf("NaN", "Infinity", "-Infinity", "N/A", "")) {
            val original = state("deepseek", "balance", QuotaWindowDisplayKind.Balance)
            val window = original.secondaryWindows.single().copy(balanceAmount = amount)
            val card = HomeViewModel().mapToUiState(original.copy(primaryWindow = window, secondaryWindows = emptyList())).quotaCards.single()
            assertEquals(HomeQuotaStatus.Unavailable, card.status)
            assertNull(card.balanceAmount)
        }
    }

    @Test
    fun `balance originals and breakdowns validate separately while legitimate negatives remain visible`() {
        val original = state("deepseek", "balance", QuotaWindowDisplayKind.Balance)
        val window = original.secondaryWindows.single().copy(
            balanceAmount = "-1.25", originalBalanceAmount = "NaN", grantedBalance = "-2.30", toppedUpBalance = "N/A",
        )
        val card = HomeViewModel().mapToUiState(original.copy(primaryWindow = window, secondaryWindows = emptyList())).quotaCards.single()
        assertEquals(HomeQuotaStatus.Exhausted, card.status)
        assertEquals("-1.25", card.balanceAmount)
        assertNull(card.originalBalanceAmount)
        assertEquals("-2.30", card.grantedBalance)
        assertNull(card.toppedUpBalance)
    }

    @Test
    fun `count-only window derives available status from verified counts without displaying a percentage`() {
        val original = state("cursor", "requests", QuotaWindowDisplayKind.UsageCount)
        for ((used, expected) in listOf(42 to HomeQuotaStatus.Normal, 72 to HomeQuotaStatus.Caution,
            95 to HomeQuotaStatus.Warning, 100 to HomeQuotaStatus.Exhausted, 120 to HomeQuotaStatus.Exhausted)) {
            val window = original.secondaryWindows.single().copy(usedCount = used, limitCount = 100, usedPercent = null)
            val card = HomeViewModel().mapToUiState(original.copy(primaryWindow = window, secondaryWindows = emptyList())).quotaCards.single()
            assertEquals(expected, card.status)
            assertEquals(used, card.usedCount)
            assertEquals(100, card.limitCount)
            assertNull(card.percent)
        }
    }

    @Test
    fun `unsupported or failed window never exposes usable values for any display kind`() {
        for (availability in listOf(QuotaWindowAvailability.Unsupported, QuotaWindowAvailability.DecodeFailed)) {
            for (kind in QuotaWindowDisplayKind.entries) {
                val original = state("codex", "one", kind)
                val window = (original.primaryWindow ?: original.secondaryWindows.single()).copy(
                    availability = availability, usedPercent = 20, balanceAmount = "12.50", balanceCurrency = "USD",
                    usedCount = 42, limitCount = 100, originalBalanceAmount = "9.20", originalBalanceCurrency = "EUR",
                    grantedBalance = "2.50", toppedUpBalance = "10.00",
                    modelBuckets = listOf(QuotaModelBucket("a", "Model A", 0.8, now.plusSeconds(3600))),
                )
                val card = HomeViewModel().mapToUiState(original.copy(primaryWindow = window, secondaryWindows = emptyList())).quotaCards.single()
                assertEquals(HomeQuotaStatus.Unavailable, card.status)
                assertNull(card.percent)
                assertNull(card.balanceAmount)
                assertNull(card.usedCount)
                assertNull(card.limitCount)
                assertNull(card.originalBalanceAmount)
                assertNull(card.grantedBalance)
                assertNull(card.toppedUpBalance)
                assertTrue(card.modelBuckets.isEmpty())
                assertEquals(window.resetAt, card.resetAt)
            }
        }
    }

    @Test
    fun `depleted official window maps to exhausted instead of unavailable`() {
        val fresh = state("codex", "one")
        val depleted = fresh.primaryWindow!!.copy(usedPercent = 100, availability = QuotaWindowAvailability.Depleted)
        val mapped = HomeViewModel().mapToUiState(fresh.copy(primaryWindow = depleted))
        assertEquals(HomeQuotaStatus.Exhausted, mapped.quotaCards.single().status)
        assertEquals(0, mapped.quotaCards.single().percent)
    }

    @Test
    fun `cached home exposes three codex accounts and mixed provider units without refreshing`() = runTest {
        val states = listOf(state("codex", "one"), state("codex", "two"), state("codex", "three"),
            state("deepseek", "balance", QuotaWindowDisplayKind.Balance),
            state("cursor", "requests", QuotaWindowDisplayKind.UsageCount),
            state("antigravity", "models", QuotaWindowDisplayKind.MultiModelFraction))
        var refreshCalls = 0
        val viewModel = HomeViewModel(
            currentQuotaStateLoader = loader(states, "two"),
            refreshUseCase = HomeRefreshUseCase { refreshCalls++; states[1] },
        )

        viewModel.loadCurrentState()
        advanceUntilIdle()

        val cards = viewModel.uiState.value.accountCards
        assertEquals(6, cards.size)
        assertEquals(3, cards.count { it.providerId == ProviderId("codex") })
        assertEquals(listOf("two"), cards.filter { it.isCurrent }.map { it.localAccountId.value })
        assertEquals("12.50", cards.single { it.localAccountId.value == "balance" }.state.quotaCards.single().balanceAmount)
        assertEquals(42, cards.single { it.localAccountId.value == "requests" }.state.quotaCards.single().usedCount)
        assertEquals("Model A", cards.single { it.localAccountId.value == "models" }.state.quotaCards.single().modelBuckets.single().displayName)
        assertEquals(0, refreshCalls)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `home displays all providers current accounts in latest Set Current order`() = runTest {
        val states = listOf(
            state("claude", "claude-old"),
            state("codex", "codex-old"),
            state("claude", "claude-selected"),
            state("codex", "codex-selected"),
        )
        val selected = listOf(
            ProviderCurrentSelection(ProviderId("claude"), LocalAccountId("claude-selected"), 1L),
            ProviderCurrentSelection(ProviderId("codex"), LocalAccountId("codex-selected"), 2L),
        )
        val loader = object : HomeCurrentQuotaStateLoader {
            override suspend fun loadCurrentState() = states.last()
            override suspend fun loadDashboardState() =
                HomeDashboardState(states.last(), states, selected)
        }
        val viewModel = HomeViewModel(currentQuotaStateLoader = loader)
        viewModel.loadCurrentState()
        advanceUntilIdle()
        val cards = viewModel.uiState.value.accountCards
        assertEquals(listOf("codex-selected", "claude-selected", "claude-old", "codex-old"),
            cards.map { it.localAccountId.value })
        assertEquals(listOf("codex-selected", "claude-selected"),
            cards.filter { it.isCurrent }.map { it.localAccountId.value })
        assertEquals(ProviderId("codex"), viewModel.uiState.value.account?.providerId)
    }

    @Test
    fun `manual date and account color flow to home without changing other accounts`() = runTest {
        val first = state("codex", "one")
        val edited = first.copy(account = first.account!!.copy(
            accentColorId = "teal",
            manualRenewalDate = "2027-01-01",
        ))
        val second = state("codex", "two")
        val viewModel = HomeViewModel(currentQuotaStateLoader = loader(listOf(edited, second), "one"))
        viewModel.loadCurrentState()
        advanceUntilIdle()
        val cards = viewModel.uiState.value.accountCards
        assertEquals("teal", cards[0].state.account?.accentColorId)
        assertEquals("2027-01-01", cards[0].state.account?.manualRenewalDate)
        assertNull(cards[1].state.account?.accentColorId)
        assertNull(cards[1].state.account?.manualRenewalDate)
    }

    @Test
    fun `each refresh completion settles only its account and keeps other cached quota`() = runTest {
        val original = listOf(state("codex", "one"), state("codex", "two"), state("codex", "three"))
        val gate = CompletableDeferred<Unit>()
        val failure = original[0].copy(status = CurrentQuotaStatus.ErrorWithLastKnownGood, error = QuotaError.Network(null))
        val refresher = object : HomeRefreshUseCase {
            override suspend fun refreshCurrentState() = original[1]
            override suspend fun refreshDashboard(onAccountRefreshed: suspend (CurrentQuotaState) -> Unit): CurrentQuotaState {
                onAccountRefreshed(failure)
                gate.await()
                onAccountRefreshed(original[1])
                onAccountRefreshed(original[2])
                return original[1]
            }
        }
        val viewModel = HomeViewModel(currentQuotaStateLoader = loader(original, "two"), refreshUseCase = refresher)
        viewModel.loadCurrentState()
        advanceUntilIdle()

        viewModel.refreshNow()
        runCurrent()

        val refreshingCards = viewModel.uiState.value.accountCards
        val refreshingById = refreshingCards.associateBy { it.localAccountId.value }
        assertFalse(refreshingById.getValue("one").state.isRefreshing)
        assertEquals(HomeContentStatus.ErrorWithLastKnownGood, refreshingById.getValue("one").state.contentStatus)
        assertTrue(refreshingById.getValue("two").state.isRefreshing)
        assertTrue(refreshingById.getValue("three").state.isRefreshing)
        assertEquals(listOf(80, 80, 80), refreshingCards.map { it.state.quotaCards.single().percent })
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.accountCards.none { it.state.isRefreshing })
        assertEquals(HomeContentStatus.ErrorWithLastKnownGood,
            viewModel.uiState.value.accountCards.first { it.localAccountId.value == "one" }.state.contentStatus)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `independent snapshot success times survive failure and only the successful account advances`() = runTest {
        val successfulTimes = listOf(now.minusSeconds(1200), now.minusSeconds(900), now.minusSeconds(600))
        val factory = CurrentQuotaStateFactory()
        val original = listOf("one", "two", "three").mapIndexed { index, id ->
            val base = state("codex", id)
            val account = requireNotNull(base.account).copy(
                lastSuccessfulRefreshAt = now.minusSeconds(7200 + index * 600L),
            )
            val cachedSnapshot = requireNotNull(base.snapshot)
            val snapshot = cachedSnapshot.copy(
                fetchedAt = successfulTimes[index],
                windows = cachedSnapshot.windows.map { it.copy(usedPercent = 20 + index * 10) },
            )
            factory.create(account, snapshot, null, now)
        }
        val failedAttempt = RefreshAttempt(
            attemptId = RefreshAttemptId("failed-one"), providerId = ProviderId("codex"),
            localAccountId = LocalAccountId("one"), trigger = RefreshTrigger.Manual,
            startedAt = now.minusSeconds(120), finishedAt = now.minusSeconds(90),
            status = RefreshAttemptStatus.Failed, errorCode = "network", httpStatus = null,
            retryable = true, userActionRequired = false, diagnosticsDigest = "safe-network-error",
        )
        val failure = factory.create(original[0].account, original[0].snapshot, failedAttempt, now)
        val successfulAt = now.minusSeconds(30)
        val newReset = now.plusSeconds(7200)
        val previousSnapshot = requireNotNull(original[1].snapshot)
        val success = factory.create(
            original[1].account,
            previousSnapshot.copy(
                snapshotId = SnapshotId("success-two"), fetchedAt = successfulAt,
                windows = previousSnapshot.windows.map { it.copy(usedPercent = 53, resetAt = newReset) },
            ),
            failedAttempt.copy(
                attemptId = RefreshAttemptId("success-two"), localAccountId = LocalAccountId("two"),
                startedAt = successfulAt.minusSeconds(15), finishedAt = successfulAt,
                status = RefreshAttemptStatus.Success, errorCode = null, retryable = null,
                userActionRequired = null, diagnosticsDigest = null,
            ),
            now,
        )
        val gate = CompletableDeferred<Unit>()
        val refresher = object : HomeRefreshUseCase {
            override suspend fun refreshCurrentState() = original[2]
            override suspend fun refreshDashboard(onAccountRefreshed: suspend (CurrentQuotaState) -> Unit): CurrentQuotaState {
                onAccountRefreshed(failure)
                gate.await()
                onAccountRefreshed(success)
                return original[2]
            }
        }
        val viewModel = HomeViewModel(currentQuotaStateLoader = loader(original, "three"), refreshUseCase = refresher)
        fun sourceOrder() = listOf("one", "two", "three").map { id ->
            viewModel.uiState.value.accountCards.first { it.localAccountId.value == id }
        }
        viewModel.loadCurrentState()
        advanceUntilIdle()
        assertEquals(successfulTimes, sourceOrder().map { it.state.refresh.lastSuccessfulRefreshAt })
        assertEquals(listOf(80, 70, 60), sourceOrder().map { it.state.quotaCards.single().percent })

        viewModel.refreshNow()
        runCurrent()
        val afterFailure = sourceOrder()
        assertEquals(successfulTimes, afterFailure.map { it.state.refresh.lastSuccessfulRefreshAt })
        assertEquals(listOf(80, 70, 60), afterFailure.map { it.state.quotaCards.single().percent })
        assertEquals(HomeContentStatus.ErrorWithLastKnownGood, afterFailure[0].state.contentStatus)
        assertEquals(listOf(failedAttempt.finishedAt, null, null), afterFailure.map { it.state.refresh.lastAttemptFinishedAt })

        gate.complete(Unit)
        advanceUntilIdle()
        val settled = sourceOrder()
        assertEquals(listOf(successfulTimes[0], successfulAt, successfulTimes[2]),
            settled.map { it.state.refresh.lastSuccessfulRefreshAt })
        assertEquals(listOf(80, 47, 60), settled.map { it.state.quotaCards.single().percent })
        assertEquals(listOf(original[0].primaryWindow!!.resetAt, newReset, original[2].primaryWindow!!.resetAt),
            settled.map { it.state.quotaCards.single().resetAt })
        assertEquals(listOf(failedAttempt.finishedAt, successfulAt, null), settled.map { it.state.refresh.lastAttemptFinishedAt })
        assertEquals(HomeContentStatus.ErrorWithLastKnownGood, settled[0].state.contentStatus)
        assertEquals(listOf("three"), settled.filter { it.isCurrent }.map { it.localAccountId.value })
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `snapshotless account falls back to its saved successful time without inventing quota`() {
        val savedSuccess = now.minusSeconds(7200)
        val account = requireNotNull(state("codex", "one").account).copy(lastSuccessfulRefreshAt = savedSuccess)
        val mapped = HomeViewModel().mapToUiState(CurrentQuotaStateFactory().create(account, null, null, now))
        assertEquals(HomeContentStatus.NoData, mapped.contentStatus)
        assertEquals(savedSuccess, mapped.refresh.lastSuccessfulRefreshAt)
        assertNull(mapped.refresh.lastAttemptFinishedAt)
        assertTrue(mapped.quotaCards.isEmpty())
    }

    @Test
    fun `progress from another account retains current cached trend without rereading it`() = runTest {
        val original = listOf(state("codex", "one"), state("codex", "two"))
        val gate = CompletableDeferred<Unit>()
        val points = listOf(HomeTrendPointUi(now.minusSeconds(3600), 3.0))
        var trendLoads = 0
        val refresh = object : HomeRefreshUseCase {
            override suspend fun refreshCurrentState() = original[1]
            override suspend fun refreshDashboard(onAccountRefreshed: suspend (CurrentQuotaState) -> Unit): CurrentQuotaState {
                onAccountRefreshed(original[0])
                gate.await()
                onAccountRefreshed(original[1])
                return original[1]
            }
        }
        val viewModel = HomeViewModel(currentQuotaStateLoader = loader(original, "two"), refreshUseCase = refresh,
            trendHistoryLoader = HomeTrendHistoryLoader { _, _ -> trendLoads++; points })
        viewModel.loadCurrentState()
        advanceUntilIdle()
        assertEquals(1, trendLoads)
        viewModel.refreshNow()
        runCurrent()
        assertEquals(points, viewModel.uiState.value.trend.points)
        assertEquals(1, trendLoads)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `refresh reentry shares the existing account sweep`() = runTest {
        val original = state("codex", "one")
        val gate = CompletableDeferred<Unit>()
        var refreshCalls = 0
        val viewModel = HomeViewModel(currentQuotaStateLoader = loader(listOf(original), "one"),
            refreshUseCase = HomeRefreshUseCase { refreshCalls++; gate.await(); original })
        viewModel.loadCurrentState()
        advanceUntilIdle()
        viewModel.refreshNow()
        viewModel.refreshNow()
        runCurrent()
        assertEquals(1, refreshCalls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `reloading after deletion removes only missing account and invalid current selection`() = runTest {
        var states = listOf(state("codex", "one"), state("codex", "two"), state("codex", "three"))
        val loader = object : HomeCurrentQuotaStateLoader {
            override suspend fun loadCurrentState() = states.first()
            override suspend fun loadDashboardState() = HomeDashboardState(
                currentState = states.first(), accountStates = states,
            )
        }
        val viewModel = HomeViewModel(currentQuotaStateLoader = loader)
        viewModel.loadCurrentState()
        advanceUntilIdle()
        states = states.drop(1)
        viewModel.loadCurrentState()
        advanceUntilIdle()
        assertEquals(listOf("two", "three"), viewModel.uiState.value.accountCards.map { it.localAccountId.value })
        assertEquals(listOf("two"), viewModel.uiState.value.accountCards.filter { it.isCurrent }.map { it.localAccountId.value })
    }

    private fun loader(states: List<CurrentQuotaState>, current: String) = object : HomeCurrentQuotaStateLoader {
        override suspend fun loadCurrentState() = states.single { it.account?.localAccountId?.value == current }
        override suspend fun loadDashboardState() = HomeDashboardState(loadCurrentState(), states)
    }

    private fun state(provider: String, id: String, kind: QuotaWindowDisplayKind = QuotaWindowDisplayKind.Percent): CurrentQuotaState {
        val account = ProviderAccount.createNew(LocalAccountId(id), ProviderId(provider), null, id, now)
        val window = QuotaWindow(
            windowId = QuotaWindowId(if (kind == QuotaWindowDisplayKind.Percent) "five_hour" else id),
            titleKey = "quota_window_five_hour", usedPercent = if (kind == QuotaWindowDisplayKind.Percent) 20 else null,
            resetAt = if (kind == QuotaWindowDisplayKind.Percent) now.plusSeconds(3600) else null,
            limitWindowSeconds = if (kind == QuotaWindowDisplayKind.Percent) 18000 else null,
            isPrimaryCandidate = true, availability = QuotaWindowAvailability.Available, displayKind = kind,
            balanceAmount = if (kind == QuotaWindowDisplayKind.Balance) "12.50" else null,
            balanceCurrency = if (kind == QuotaWindowDisplayKind.Balance) "USD" else null,
            usedCount = if (kind == QuotaWindowDisplayKind.UsageCount) 42 else null,
            limitCount = if (kind == QuotaWindowDisplayKind.UsageCount) 100 else null,
            modelBuckets = if (kind == QuotaWindowDisplayKind.MultiModelFraction) listOf(QuotaModelBucket("a", "Model A", 0.6, now.plusSeconds(3600))) else emptyList(),
        )
        val snapshot = QuotaSnapshot(SnapshotId("snapshot-$id"), account.providerId, account.localAccountId, null,
            now.minusSeconds(600), QuotaSnapshotSource.ManualRefresh, "plus", listOf(window), null, null)
        return CurrentQuotaStateFactory().create(account, snapshot, null, now)
    }
}
