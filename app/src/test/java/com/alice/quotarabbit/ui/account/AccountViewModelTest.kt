package com.alice.quotarabbit.ui.account

import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.account.AccountDeleteUseCase
import com.alice.quotarabbit.domain.account.AccountListResult
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.account.AccountRenameUseCase
import com.alice.quotarabbit.domain.account.AccountEditUseCase
import com.alice.quotarabbit.domain.account.AccountEditInput
import com.alice.quotarabbit.domain.account.AccountSwitchUseCase
import com.alice.quotarabbit.domain.account.ProviderCurrentSelection
import com.alice.quotarabbit.domain.account.NoopAccountDeleteUseCase
import com.alice.quotarabbit.domain.account.NoopAccountListUseCase
import com.alice.quotarabbit.domain.account.NoopAccountRenameUseCase
import com.alice.quotarabbit.domain.account.NoopAccountSwitchUseCase
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.model.SnapshotId
import com.alice.quotarabbit.domain.quota.Credits
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.settings.NotificationPreferenceStore
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountViewModelTest {
    @Test
    fun `switch current account persists selection before marking selected account current`() = runTest {
        val switchUseCase = RecordingAccountSwitchUseCase()
        val viewModel = viewModel(switchUseCase = switchUseCase)
        viewModel.setAccounts(accounts = listOf(account("local-1"), account("local-2")), currentAccountId = localId("local-1"))

        viewModel.switchCurrentAccount(localId("local-2"))

        assertEquals(
            listOf(AccountSwitchRequest(providerId = ProviderId("codex"), localAccountId = localId("local-2"))),
            switchUseCase.requests,
        )
        val uiState = viewModel.uiState.value
        assertEquals(localId("local-2"), uiState.currentAccountId)
        assertEquals(AccountContentStatus.HasAccounts, uiState.contentStatus)
        assertTrue(uiState.accounts.first { it.id == localId("local-2") }.isCurrent)
        assertEquals(false, uiState.accounts.first { it.id == localId("local-1") }.isCurrent)
    }

    @Test
    fun `multiple providers keep separate current accounts and latest choice sorts first`() = runTest {
        val viewModel = viewModel(switchUseCase = RecordingAccountSwitchUseCase())
        val accounts = listOf(account("codex-old"), account("codex-new"),
            account("claude-one").copy(providerId = ProviderId("claude")))
        viewModel.setAccounts(
            accounts = accounts,
            currentAccountId = localId("claude-one"),
            providerCurrentSelections = listOf(
                ProviderCurrentSelection(ProviderId("codex"), localId("codex-old"), 1L),
                ProviderCurrentSelection(ProviderId("claude"), localId("claude-one"), 2L),
            ),
        )
        assertEquals(listOf("claude-one", "codex-old", "codex-new"),
            viewModel.uiState.value.accounts.map { it.id.value })
        viewModel.switchCurrentAccount(localId("codex-new"))
        assertEquals(listOf("codex-new", "claude-one", "codex-old"),
            viewModel.uiState.value.accounts.map { it.id.value })
        assertEquals(listOf("codex-new", "claude-one"),
            viewModel.uiState.value.accounts.filter { it.isCurrent }.map { it.id.value })
        // Repeating Set Current for a provider's already-current account reprioritizes All.
        viewModel.switchCurrentAccount(localId("claude-one"))
        assertEquals(listOf("claude-one", "codex-new", "codex-old"),
            viewModel.uiState.value.accounts.map { it.id.value })
    }

    @Test
    fun `switch failure keeps previous current account`() = runTest {
        val viewModel = viewModel(switchUseCase = RecordingAccountSwitchUseCase(succeed = false))
        viewModel.setAccounts(accounts = listOf(account("local-1"), account("local-2")), currentAccountId = localId("local-1"))

        viewModel.switchCurrentAccount(localId("local-2"))

        assertEquals(localId("local-1"), viewModel.uiState.value.currentAccountId)
    }

    @Test
    fun `rename account persists change and uses returned account`() = runTest {
        val renamedAccount = account("local-1", displayName = "Work")
        val renameUseCase = RecordingAccountRenameUseCase(renamedAccount)
        val viewModel = viewModel(renameUseCase = renameUseCase)
        viewModel.setAccounts(accounts = listOf(account("local-1", displayName = "Codex Main")), currentAccountId = localId("local-1"))

        viewModel.renameAccount(localId("local-1"), "Work")

        assertEquals(
            listOf(AccountRenameRequest(ProviderId("codex"), localId("local-1"), "Work")),
            renameUseCase.requests,
        )
        val account = viewModel.uiState.value.accounts.single()
        assertEquals("Work", account.displayName)
        assertEquals("W", account.avatarInitial)
        assertEquals(localId("local-1"), viewModel.uiState.value.currentAccountId)
    }

    @Test
    fun `rename failure keeps pending dialog open`() = runTest {
        val viewModel = viewModel(renameUseCase = RecordingAccountRenameUseCase(result = null))
        viewModel.setAccounts(accounts = listOf(account("local-1", displayName = "Codex Main")), currentAccountId = localId("local-1"))
        viewModel.requestRenameAccount(localId("local-1"))

        viewModel.renameAccount(localId("local-1"), "Work")

        val uiState = viewModel.uiState.value
        assertEquals("Codex Main", uiState.accounts.single().displayName)
        assertEquals(localId("local-1"), uiState.pendingRenameAccount?.accountId)
    }

    @Test
    fun `loading accounts uses persisted account list and current selection`() = runTest {
        val accountListUseCase = RecordingAccountListUseCase(
            AccountListResult(
                accounts = listOf(account("local-1"), account("local-2")),
                currentAccountId = localId("local-2"),
            ),
        )
        val viewModel = viewModel(accountListUseCase = accountListUseCase)

        viewModel.loadAccounts()

        val uiState = viewModel.uiState.value
        assertEquals(1, accountListUseCase.loadCount)
        assertEquals(AccountContentStatus.HasAccounts, uiState.contentStatus)
        assertEquals(localId("local-2"), uiState.currentAccountId)
        assertEquals(listOf(localId("local-2"), localId("local-1")), uiState.accounts.map { it.id })
        assertTrue(uiState.accounts.first { it.id == localId("local-2") }.isCurrent)
    }

    @Test
    fun `loading accounts maps latest quota snapshots to remaining percent summaries`() = runTest {
        val accountListUseCase = RecordingAccountListUseCase(
            AccountListResult(
                accounts = listOf(account("local-1")),
                currentAccountId = localId("local-1"),
                latestQuotaSnapshots = mapOf(
                    localId("local-1") to quotaSnapshot(
                        localAccountId = "local-1",
                        fiveHourUsedPercent = 62,
                        weeklyUsedPercent = 41,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(accountListUseCase = accountListUseCase)

        viewModel.loadAccounts()

        val summaries = viewModel.uiState.value.accounts.single().quotaSummaries
        assertEquals(38, summaries.first { it.labelResId == R.string.account_quota_five_hour_label }.percent)
        assertEquals(59, summaries.first { it.labelResId == R.string.account_quota_weekly_label }.percent)
    }

    @Test
    fun `loading accounts exposes mapped Codex plan type from latest snapshot`() = runTest {
        val accountListUseCase = RecordingAccountListUseCase(
            AccountListResult(
                accounts = listOf(account("prolite"), account("pro")),
                currentAccountId = localId("local-1"),
                latestQuotaSnapshots = mapOf(
                    localId("prolite") to quotaSnapshot(
                        localAccountId = "prolite",
                        fiveHourUsedPercent = 62,
                        weeklyUsedPercent = 41,
                        planType = "prolite",
                    ),
                    localId("pro") to quotaSnapshot(
                        localAccountId = "pro",
                        fiveHourUsedPercent = 62,
                        weeklyUsedPercent = 41,
                        planType = "pro",
                    ),
                ),
            ),
        )
        val viewModel = viewModel(accountListUseCase = accountListUseCase)

        viewModel.loadAccounts()

        val accounts = viewModel.uiState.value.accounts.associateBy { it.id }
        assertEquals("Pro 5x", accounts.getValue(localId("prolite")).planType)
        assertEquals("Pro 20x", accounts.getValue(localId("pro")).planType)
    }

    @Test
    fun `account cards are collapsed by default and can be toggled open`() {
        val viewModel = viewModel()
        viewModel.setAccounts(accounts = listOf(account("local-1"), account("local-2")), currentAccountId = localId("local-1"))

        assertEquals(false, viewModel.uiState.value.accounts.first { it.id == localId("local-1") }.isExpanded)
        assertEquals(false, viewModel.uiState.value.accounts.first { it.id == localId("local-2") }.isExpanded)

        viewModel.toggleAccountExpanded(localId("local-1"))

        assertEquals(true, viewModel.uiState.value.accounts.first { it.id == localId("local-1") }.isExpanded)
        assertEquals(false, viewModel.uiState.value.accounts.first { it.id == localId("local-2") }.isExpanded)

        viewModel.toggleAccountExpanded(localId("local-1"))

        assertEquals(false, viewModel.uiState.value.accounts.first { it.id == localId("local-1") }.isExpanded)
    }

    @Test
    fun `loading accounts exposes credits balance unlimited and unavailable states`() = runTest {
        val accountListUseCase = RecordingAccountListUseCase(
            AccountListResult(
                accounts = listOf(account("balance"), account("unlimited"), account("missing")),
                currentAccountId = localId("balance"),
                latestQuotaSnapshots = mapOf(
                    localId("balance") to quotaSnapshot(
                        localAccountId = "balance",
                        fiveHourUsedPercent = 62,
                        weeklyUsedPercent = 41,
                        credits = Credits(hasCredits = true, unlimited = false, balance = 125.5),
                    ),
                    localId("unlimited") to quotaSnapshot(
                        localAccountId = "unlimited",
                        fiveHourUsedPercent = 62,
                        weeklyUsedPercent = 41,
                        credits = Credits(hasCredits = true, unlimited = true, balance = null),
                    ),
                    localId("missing") to quotaSnapshot(
                        localAccountId = "missing",
                        fiveHourUsedPercent = 62,
                        weeklyUsedPercent = 41,
                        credits = null,
                    ),
                ),
            ),
        )
        val viewModel = viewModel(accountListUseCase = accountListUseCase)

        viewModel.loadAccounts()

        val accounts = viewModel.uiState.value.accounts.associateBy { it.id }
        assertEquals(AccountCreditsUi.Balance(125.5), accounts.getValue(localId("balance")).credits)
        assertEquals(AccountCreditsUi.Unlimited, accounts.getValue(localId("unlimited")).credits)
        assertEquals(AccountCreditsUi.Unavailable, accounts.getValue(localId("missing")).credits)
    }

    @Test
    fun `loading accounts publishes custom thresholds for quota gauge colors`() = runTest {
        val preferences = NotificationPreferences(cautionThreshold = 68, warningThreshold = 24)
        val viewModel = viewModel(
            notificationPreferenceStore = RecordingNotificationPreferenceStore(preferences),
        )

        viewModel.loadAccounts()

        assertEquals(preferences.cautionThreshold, viewModel.uiState.value.quotaColorPreferences.cautionThreshold)
        assertEquals(preferences.warningThreshold, viewModel.uiState.value.quotaColorPreferences.warningThreshold)
    }

    @Test
    fun `loading accounts exposes persisted quota alert switches`() = runTest {
        val preferences = NotificationPreferences()
            .withAccountQuotaAlertEnabled(
                providerId = ProviderId("codex"),
                localAccountId = localId("local-1"),
                windowId = QuotaWindowId("five_hour"),
                enabled = false,
            )
            .withAccountQuotaAlertEnabled(
                providerId = ProviderId("codex"),
                localAccountId = localId("local-1"),
                windowId = QuotaWindowId("weekly"),
                enabled = true,
            )
        val viewModel = viewModel(
            accountListUseCase = RecordingAccountListUseCase(
                AccountListResult(
                    accounts = listOf(account("local-1")),
                    currentAccountId = localId("local-1"),
                    latestQuotaSnapshots = mapOf(
                        localId("local-1") to quotaSnapshot(
                            localAccountId = "local-1",
                            fiveHourUsedPercent = 50,
                            weeklyUsedPercent = 50,
                        ),
                    ),
                ),
            ),
            notificationPreferenceStore = RecordingNotificationPreferenceStore(preferences),
        )

        viewModel.loadAccounts()

        val account = viewModel.uiState.value.accounts.single()
        assertEquals(false, account.alertToggles.first { it.windowId == "five_hour" }.enabled)
        assertEquals(true, account.alertToggles.first { it.windowId == "weekly" }.enabled)
    }

    @Test
    fun `toggling five hour and weekly alerts persists account window preference`() = runTest {
        val store = RecordingNotificationPreferenceStore(NotificationPreferences())
        val viewModel = viewModel(notificationPreferenceStore = store)
        viewModel.setAccounts(
            accounts = listOf(account("local-1")),
            currentAccountId = localId("local-1"),
            latestQuotaSnapshots = mapOf(
                localId("local-1") to quotaSnapshot(
                    localAccountId = "local-1",
                    fiveHourUsedPercent = 50,
                    weeklyUsedPercent = 50,
                ),
            ),
        )

        viewModel.setQuotaAlertEnabled(localId("local-1"), QuotaWindowId("five_hour"), false)
        viewModel.setQuotaAlertEnabled(localId("local-1"), QuotaWindowId("weekly"), true)

        assertEquals(
            false,
            store.preferences.isQuotaAlertEnabled(
                providerId = ProviderId("codex"),
                localAccountId = localId("local-1"),
                windowId = QuotaWindowId("five_hour"),
            ),
        )
        assertEquals(
            true,
            store.preferences.isQuotaAlertEnabled(
                providerId = ProviderId("codex"),
                localAccountId = localId("local-1"),
                windowId = QuotaWindowId("weekly"),
            ),
        )
        val account = viewModel.uiState.value.accounts.single()
        assertEquals(false, account.alertToggles.first { it.windowId == "five_hour" }.enabled)
        assertEquals(true, account.alertToggles.first { it.windowId == "weekly" }.enabled)
    }

    @Test
    fun `enabling an alert switch requests immediate evaluation for account window`() = runTest {
        val requester = RecordingAccountQuotaAlertEvaluationRequester()
        val viewModel = viewModel(
            notificationPreferenceStore = RecordingNotificationPreferenceStore(NotificationPreferences()),
            quotaAlertEvaluationRequester = requester,
        )
        viewModel.setAccounts(accounts = listOf(account("local-1")), currentAccountId = localId("local-1"))

        viewModel.setQuotaAlertEnabled(localId("local-1"), QuotaWindowId("weekly"), true)

        assertEquals(
            listOf(AccountQuotaAlertEvaluationRequest(ProviderId("codex"), localId("local-1"), QuotaWindowId("weekly"))),
            requester.requests,
        )
    }

    @Test
    fun `deleting current account invokes delete use case and selects another account when available`() = runTest {
        val deleteUseCase = RecordingAccountDeleteUseCase()
        val viewModel = viewModel(deleteUseCase)
        viewModel.setAccounts(accounts = listOf(account("local-1"), account("local-2")), currentAccountId = localId("local-1"))

        viewModel.requestDeleteAccount(localId("local-1"))
        viewModel.confirmDelete()

        assertEquals(
            listOf(AccountDeleteRequest(providerId = ProviderId("codex"), localAccountId = localId("local-1"))),
            deleteUseCase.requests,
        )
        val uiState = viewModel.uiState.value
        assertEquals(AccountContentStatus.HasAccounts, uiState.contentStatus)
        assertEquals(localId("local-2"), uiState.currentAccountId)
        assertEquals(listOf(localId("local-2")), uiState.accounts.map { it.id })
        assertTrue(uiState.accounts.single().isCurrent)
        assertNull(uiState.pendingDeleteAccount)
    }

    @Test
    fun `deleting last account returns unauthenticated state`() = runTest {
        val viewModel = viewModel()
        viewModel.setAccounts(accounts = listOf(account("local-1")), currentAccountId = localId("local-1"))

        viewModel.requestDeleteAccount(localId("local-1"))
        viewModel.confirmDelete()

        val uiState = viewModel.uiState.value
        assertEquals(AccountContentStatus.Unauthenticated, uiState.contentStatus)
        assertNull(uiState.currentAccountId)
        assertTrue(uiState.accounts.isEmpty())
        assertNull(uiState.pendingDeleteAccount)
    }

    @Test
    fun `delete use case failure keeps account and pending confirmation`() = runTest {
        val viewModel = viewModel(ThrowingAccountDeleteUseCase(IllegalStateException("delete failed")))
        viewModel.setAccounts(accounts = listOf(account("local-1")), currentAccountId = localId("local-1"))

        viewModel.requestDeleteAccount(localId("local-1"))
        viewModel.confirmDelete()

        val uiState = viewModel.uiState.value
        assertEquals(AccountContentStatus.HasAccounts, uiState.contentStatus)
        assertEquals(listOf(localId("local-1")), uiState.accounts.map { it.id })
        assertEquals(localId("local-1"), uiState.currentAccountId)
        assertEquals(localId("local-1"), uiState.pendingDeleteAccount?.accountId)
    }

    @Test
    fun `request and dismiss add account toggles add sheet state`() {
        val viewModel = viewModel()
        viewModel.requestAddAccount()

        assertTrue(viewModel.uiState.value.showAddAccountSheet)

        viewModel.dismissAddAccount()

        assertEquals(false, viewModel.uiState.value.showAddAccountSheet)
        assertNull(viewModel.uiState.value.selectedAddAccountIntent)
    }

    @Test
    fun `add account intents only include login`() {
        assertEquals(listOf(AccountAddAccountIntent.LoginToCodex), AccountAddAccountIntent.entries)
    }

    @Test
    fun `selecting relogin emits a request targeting that account and closes the sheet`() {
        val viewModel = viewModel()
        viewModel.setAccounts(accounts = listOf(account("local-1")), currentAccountId = localId("local-1"))
        viewModel.requestAddAccount()

        viewModel.selectRelogin(localId("local-1"))

        assertEquals(false, viewModel.uiState.value.showAddAccountSheet)
        val request = viewModel.uiState.value.pendingReloginRequest
        assertEquals(ProviderId("codex"), request?.providerId)
        assertEquals(localId("local-1"), request?.localAccountId)
        assertEquals("provider-local-1", request?.providerAccountId)
    }

    @Test
    fun `consuming the relogin request clears it`() {
        val viewModel = viewModel()
        viewModel.setAccounts(accounts = listOf(account("local-1")), currentAccountId = localId("local-1"))
        viewModel.selectRelogin(localId("local-1"))

        viewModel.consumeReloginRequest()

        assertEquals(null, viewModel.uiState.value.pendingReloginRequest)
    }

    @Test
    fun `phase05 three cached Codex accounts retain independent remaining quota and reset`() {
        val ids = listOf("personal", "work", "team")
        val snapshots = listOf(18, 47, 93).mapIndexed { index, used ->
            localId(ids[index]) to quotaSnapshot(ids[index], fiveHourUsedPercent = 10 + index, weeklyUsedPercent = used)
        }.toMap()
        val viewModel = viewModel()
        viewModel.setAccounts(ids.map { account(it) }, localId("work"), snapshots)

        val items = viewModel.uiState.value.accounts
        assertEquals(listOf("work", "personal", "team"), items.map { it.id.value })
        assertEquals(listOf(53, 82, 7), items.map { item ->
            item.quotaSummaries.single { it.windowId == "weekly" }.percent
        })
        assertEquals(ids.map { ProviderId("codex") }, items.map { it.providerId })
        assertEquals(Instant.parse("2026-05-23T12:00:00Z"),
            items.first().quotaSummaries.single { it.windowId == "weekly" }.resetAt)
        assertEquals(listOf(true, false, false), items.map { it.isCurrent })
    }

    @Test
    fun `phase05 mixed provider does not inherit Codex-only windows`() {
        val other = ProviderAccount.createNew(
            localAccountId = localId("balance"),
            providerId = ProviderId("deepseek"),
            providerAccountId = ProviderAccountId("other-id"),
            displayName = "DeepSeek",
            now = Instant.parse("2026-05-23T09:00:00Z"),
        )
        val viewModel = viewModel()
        viewModel.setAccounts(listOf(account("codex"), other), localId("codex"))
        val items = viewModel.uiState.value.accounts
        assertEquals(ProviderId("deepseek"), items.last().providerId)
        assertTrue(items.last().quotaSummaries.isEmpty())
        assertNull(items.last().lastSuccessfulRefreshAt)
    }

    @Test
    fun `edit cancel does not call persistent use case`() = runTest {
        var writes = 0
        val editor = AccountEditUseCase { _, _, _ -> writes++; null }
        val viewModel = viewModel(editUseCase = editor)
        viewModel.setAccounts(listOf(account("one")), localId("one"))
        viewModel.requestEditAccount(localId("one"))
        assertEquals(localId("one"), viewModel.uiState.value.pendingEditAccount?.id)
        viewModel.cancelEdit()
        assertNull(viewModel.uiState.value.pendingEditAccount)
        assertEquals(0, writes)
    }

    @Test
    fun `edit persists only target account and supports clearing renewal`() = runTest {
        val updates = mutableListOf<Pair<LocalAccountId, AccountEditInput>>()
        val editor = AccountEditUseCase { provider, id, input ->
            assertEquals(ProviderId("codex"), provider)
            updates += id to input
            account(id.value, input.nickname).copy(
                accentColorId = input.accentColorId,
                manualRenewalDate = input.manualRenewalDate,
            )
        }
        val viewModel = viewModel(editUseCase = editor)
        viewModel.setAccounts(listOf(account("one"), account("two")), localId("one"))
        viewModel.requestEditAccount(localId("two"))
        viewModel.saveEdit(localId("two"), AccountEditInput("Team", "rose", "2027-01-01"))
        assertEquals("Team", viewModel.uiState.value.accounts[1].displayName)
        assertEquals("rose", viewModel.uiState.value.accounts[1].accentColorId)
        assertEquals("2027-01-01", viewModel.uiState.value.accounts[1].manualRenewalDate)
        assertNull(viewModel.uiState.value.accounts[0].accentColorId)
        assertNull(viewModel.uiState.value.pendingEditAccount)
        viewModel.requestEditAccount(localId("two"))
        viewModel.saveEdit(localId("two"), AccountEditInput("Team", null, null))
        assertNull(viewModel.uiState.value.accounts[1].manualRenewalDate)
        assertNull(viewModel.uiState.value.accounts[1].accentColorId)
        assertEquals(listOf(localId("two"), localId("two")), updates.map { it.first })
    }

    private class RecordingAccountDeleteUseCase : AccountDeleteUseCase {
        val requests = mutableListOf<AccountDeleteRequest>()

        override suspend fun deleteAccount(providerId: ProviderId, localAccountId: LocalAccountId) {
            requests += AccountDeleteRequest(providerId = providerId, localAccountId = localAccountId)
        }
    }

    private class ThrowingAccountDeleteUseCase(
        private val exception: Throwable,
    ) : AccountDeleteUseCase {
        override suspend fun deleteAccount(providerId: ProviderId, localAccountId: LocalAccountId) {
            throw exception
        }
    }

    private data class AccountDeleteRequest(
        val providerId: ProviderId,
        val localAccountId: LocalAccountId,
    )

    private class RecordingAccountListUseCase(
        private val result: AccountListResult,
    ) : AccountListUseCase {
        var loadCount = 0

        override suspend fun loadAccounts(): AccountListResult {
            loadCount += 1
            return result
        }
    }

    private class RecordingAccountSwitchUseCase(
        private val succeed: Boolean = true,
    ) : AccountSwitchUseCase {
        val requests = mutableListOf<AccountSwitchRequest>()

        override suspend fun switchCurrentAccount(providerId: ProviderId, localAccountId: LocalAccountId): Boolean {
            requests += AccountSwitchRequest(providerId = providerId, localAccountId = localAccountId)
            return succeed
        }
    }

    private class RecordingAccountRenameUseCase(
        private val result: ProviderAccount?,
    ) : AccountRenameUseCase {
        val requests = mutableListOf<AccountRenameRequest>()

        override suspend fun renameAccount(
            providerId: ProviderId,
            localAccountId: LocalAccountId,
            displayName: String,
        ): ProviderAccount? {
            requests += AccountRenameRequest(providerId = providerId, localAccountId = localAccountId, displayName = displayName)
            return result
        }
    }

    private data class AccountSwitchRequest(
        val providerId: ProviderId,
        val localAccountId: LocalAccountId,
    )

    private data class AccountRenameRequest(
        val providerId: ProviderId,
        val localAccountId: LocalAccountId,
        val displayName: String,
    )

    private class RecordingNotificationPreferenceStore(
        var preferences: NotificationPreferences,
    ) : NotificationPreferenceStore {
        override suspend fun notificationPreferences(): NotificationPreferences = preferences

        override suspend fun updateNotificationPreferences(preferences: NotificationPreferences) {
            this.preferences = preferences
        }
    }

    private class RecordingAccountQuotaAlertEvaluationRequester : AccountQuotaAlertEvaluationRequester {
        val requests = mutableListOf<AccountQuotaAlertEvaluationRequest>()

        override suspend fun requestQuotaAlertEvaluation(
            providerId: ProviderId,
            localAccountId: LocalAccountId,
            windowId: QuotaWindowId,
        ) {
            requests += AccountQuotaAlertEvaluationRequest(providerId, localAccountId, windowId)
        }
    }

    private data class AccountQuotaAlertEvaluationRequest(
        val providerId: ProviderId,
        val localAccountId: LocalAccountId,
        val windowId: QuotaWindowId,
    )

    private fun viewModel(
        deleteUseCase: AccountDeleteUseCase = NoopAccountDeleteUseCase,
        accountListUseCase: AccountListUseCase = NoopAccountListUseCase,
        switchUseCase: AccountSwitchUseCase = NoopAccountSwitchUseCase,
        renameUseCase: AccountRenameUseCase = NoopAccountRenameUseCase,
        editUseCase: AccountEditUseCase = AccountEditUseCase { _, _, _ -> null },
        notificationPreferenceStore: NotificationPreferenceStore = AccountInMemoryNotificationPreferenceStore(),
        quotaAlertEvaluationRequester: AccountQuotaAlertEvaluationRequester = NoopAccountQuotaAlertEvaluationRequester,
    ): AccountViewModel =
        AccountViewModel(
            deleteUseCase = deleteUseCase,
            accountListUseCase = accountListUseCase,
            switchUseCase = switchUseCase,
            renameUseCase = renameUseCase,
            editUseCase = editUseCase,
            notificationPreferenceStore = notificationPreferenceStore,
            quotaAlertEvaluationRequester = quotaAlertEvaluationRequester,
        )

    private fun account(id: String, displayName: String = "Account $id"): ProviderAccount =
        ProviderAccount.createNew(
            localAccountId = localId(id),
            providerId = ProviderId("codex"),
            providerAccountId = ProviderAccountId("provider-$id"),
            displayName = displayName,
            now = Instant.parse("2026-05-23T09:00:00Z"),
        )

    private fun quotaSnapshot(
        localAccountId: String,
        fiveHourUsedPercent: Int?,
        weeklyUsedPercent: Int?,
        planType: String? = "plus",
        credits: Credits? = null,
    ): QuotaSnapshot =
        QuotaSnapshot(
            snapshotId = SnapshotId("snapshot-$localAccountId"),
            providerId = ProviderId("codex"),
            localAccountId = localId(localAccountId),
            providerAccountId = ProviderAccountId("provider-$localAccountId"),
            fetchedAt = Instant.parse("2026-05-23T10:00:00Z"),
            source = QuotaSnapshotSource.ManualRefresh,
            planType = planType,
            windows = listOf(
                quotaWindow("five_hour", fiveHourUsedPercent, limitWindowSeconds = 18_000),
                quotaWindow("weekly", weeklyUsedPercent, limitWindowSeconds = 604_800),
            ),
            credits = credits,
            responseDigest = "safe-digest",
        )

    private fun quotaWindow(
        windowId: String,
        usedPercent: Int?,
        limitWindowSeconds: Int = 18_000,
    ): QuotaWindow =
        QuotaWindow(
            windowId = QuotaWindowId(windowId),
            titleKey = "quota_window_$windowId",
            usedPercent = usedPercent,
            resetAt = Instant.parse("2026-05-23T12:00:00Z"),
            limitWindowSeconds = limitWindowSeconds,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
        )

    private fun localId(id: String): LocalAccountId = LocalAccountId(id)
}
