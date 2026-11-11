package com.alice.quotarabbit.widget

import com.alice.quotarabbit.domain.model.AccountStatus
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.model.SnapshotId
import com.alice.quotarabbit.domain.quota.CurrentQuotaFreshness
import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.quota.CurrentQuotaStatus
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.domain.quota.QuotaModelBucket
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetQuotaStateFactoryTest {
    private val factory = WidgetQuotaStateFactory()

    @Test
    fun `disabled account does not claim connected fresh quota`() {
        val state = freshState(listOf(percentWindow("five_hour", 20)))
        val result = factory.create(state.copy(account = state.account?.copy(status = AccountStatus.Disabled)))
        assertEquals("Disconnected", result.status.name)
        assertEquals(WidgetQuotaTone.Neutral, result.tone)
        assertEquals(80, result.fields.single().percent)
    }

    @Test
    fun `builds fields from selected windows in snapshot order`() {
        val state = freshState(
            windows = listOf(
                percentWindow("five_hour", usedPercent = 13),
                percentWindow("weekly", usedPercent = 58),
                percentWindow("monthly", usedPercent = 29),
            ),
        )
        val result = factory.create(state, selectedWindowIds = listOf("weekly", "five_hour"))

        // 顺序按 snapshot 天然顺序（five_hour 在前），不按选择顺序。
        assertEquals(listOf("five_hour", "weekly"), result.fields.map { it.windowId })
        assertEquals(87, result.fields[0].percent) // 100 - 13
        assertFalse(result.fields[0].isBalance)
    }

    @Test
    fun `usage count window retains provider used and limit counts`() {
        val state = freshState(
            windows = listOf(usageCountWindow("calls", usedPercent = 40, usedCount = 40, limitCount = 100)),
        )
        val result = factory.create(state, selectedWindowIds = listOf("calls"))
        assertEquals(1, result.fields.size)
        assertFalse(result.fields[0].isBalance)
        assertEquals(60, result.fields[0].percent)
        assertEquals(QuotaWindowDisplayKind.UsageCount, result.fields[0].displayKind)
        assertEquals(40, result.fields[0].usedCount)
        assertEquals(100, result.fields[0].limitCount)
    }

    @Test
    fun `loading retains known fields without claiming fresh data`() {
        val state = freshState(listOf(percentWindow("five_hour", 20))).copy(status = CurrentQuotaStatus.Loading)
        val result = factory.create(state)
        assertEquals(WidgetQuotaStatus.Loading, result.status)
        assertEquals(80, result.fields.single().percent)
    }

    @Test
    fun `decode failed field stays explicitly unavailable`() {
        val window = percentWindow("weekly", 20).copy(usedPercent = null, availability = QuotaWindowAvailability.DecodeFailed)
        val result = factory.create(freshState(listOf(window)))
        assertEquals(1, result.fields.size)
        assertEquals(null, result.fields.single().percent)
        assertEquals(WidgetQuotaTone.Neutral, result.fields.single().tone)
    }

    @Test
    fun `multi-model fields preserve individual remaining quota and resets`() {
        val reset = Instant.parse("2026-06-02T14:30:00Z")
        val window = percentWindow("models", 55).copy(
            displayKind = QuotaWindowDisplayKind.MultiModelFraction,
            modelBuckets = listOf(
                QuotaModelBucket("a", "Model A", 0.8, reset),
                QuotaModelBucket("b", "Model B", 0.45, null),
            ),
        )
        val result = factory.create(freshState(listOf(window)))
        assertEquals(listOf("Model A", "Model B"), result.fields.map { it.modelName })
        assertEquals(listOf(80, 45), result.fields.map { it.percent })
        assertEquals(listOf(reset, null), result.fields.map { it.resetAt })
    }

    @Test
    fun `unavailable multi-model buckets do not become available percentages`() {
        for (availability in listOf(QuotaWindowAvailability.Unsupported, QuotaWindowAvailability.DecodeFailed)) {
            val window = percentWindow("models", 55).copy(
                availability = availability,
                displayKind = QuotaWindowDisplayKind.MultiModelFraction,
                modelBuckets = listOf(QuotaModelBucket("a", "Model A", 0.8, null)),
            )
            val field = factory.create(freshState(listOf(window))).fields.single()
            assertEquals(null, field.percent)
            assertEquals(WidgetQuotaTone.Neutral, field.tone)
        }
    }

    @Test
    fun `balance tone follows balance thresholds`() {
        val preferences = NotificationPreferences(
            balanceCautionThreshold = 10.0,
            balanceWarningThreshold = 2.0,
        )

        // 镜像 Home 的余额档位：耗尽与紧张同为 Danger（与百分比路径的简化一致），注意为 Warning，健康为 Success。
        assertEquals(WidgetQuotaTone.Danger, balanceTone("0.00", preferences))
        assertEquals(WidgetQuotaTone.Danger, balanceTone("1.50", preferences))
        assertEquals(WidgetQuotaTone.Warning, balanceTone("9.00", preferences))
        assertEquals(WidgetQuotaTone.Success, balanceTone("42.00", preferences))
    }

    @Test
    fun `unreadable balance amount stays neutral`() {
        val preferences = NotificationPreferences(
            balanceCautionThreshold = 10.0,
            balanceWarningThreshold = 2.0,
        )

        assertEquals(WidgetQuotaTone.Neutral, balanceTone("N/A", preferences))
    }

    @Test
    fun `malformed and nonfinite balance values remain unavailable and neutral`() {
        for (amount in listOf("N/A", "NaN", "Infinity", "-Infinity", "1e9999")) {
            val field = factory.create(freshState(listOf(balanceWindow("balance", amount, "USD")))).fields.single()
            assertEquals(null, field.balanceAmount)
            assertEquals(WidgetQuotaTone.Neutral, field.tone)
        }
    }

    @Test
    fun `balance window renders as amount`() {
        val state = freshState(
            windows = listOf(balanceWindow("balance", amount = "8.50", currency = "USD")),
        )
        val result = factory.create(state, selectedWindowIds = listOf("balance"))
        assertTrue(result.fields[0].isBalance)
        assertEquals("8.50", result.fields[0].balanceAmount)
        assertEquals("USD", result.fields[0].balanceCurrency)
    }

    @Test
    fun `fields carry provider-reported window duration`() {
        val state = freshState(
            windows = listOf(percentWindow("five_hour", usedPercent = 41).copy(limitWindowSeconds = 604800)),
        )
        val result = factory.create(state, selectedWindowIds = listOf("five_hour"))
        assertEquals(604800, result.fields[0].limitWindowSeconds)
    }

    @Test
    fun `unavailable windows are excluded`() {
        val state = freshState(
            windows = listOf(
                percentWindow("five_hour", usedPercent = 10),
                QuotaWindow(
                    windowId = QuotaWindowId("weekly"), titleKey = "weekly", usedPercent = null,
                    resetAt = null, limitWindowSeconds = null, isPrimaryCandidate = false,
                    availability = QuotaWindowAvailability.Missing,
                ),
            ),
        )
        val result = factory.create(state, selectedWindowIds = listOf("five_hour", "weekly"))
        assertEquals(listOf("five_hour"), result.fields.map { it.windowId })
    }

    @Test
    fun `unconfigured with accounts yields onboarding state`() {
        val result = factory.unconfigured(hasAccounts = true)
        assertTrue(result.isUnconfigured)
        assertTrue(result.hasAccounts)
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun `unconfigured without accounts opens home rather than provider selection`() {
        val result = factory.unconfigured(hasAccounts = false)
        assertTrue(result.isUnconfigured)
        assertFalse(result.hasAccounts)
        assertEquals(WidgetClickTarget.Home, result.clickTarget)
    }

    // ---- helpers ----
    private fun freshState(windows: List<QuotaWindow>): CurrentQuotaState {
        val account = ProviderAccount(
            localAccountId = LocalAccountId("acc-1"),
            providerId = ProviderId("codex"),
            providerAccountId = ProviderAccountId("p-1"),
            displayName = "个人号",
            avatarInitial = "个",
            avatarColorKey = "acc-1",
            status = AccountStatus.Active,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-06-01T00:00:00Z"),
            lastSuccessfulRefreshAt = Instant.parse("2026-06-02T10:00:00Z"),
        )
        val snapshot = QuotaSnapshot(
            snapshotId = SnapshotId("s-1"),
            providerId = ProviderId("codex"),
            localAccountId = LocalAccountId("acc-1"),
            providerAccountId = ProviderAccountId("p-1"),
            fetchedAt = Instant.parse("2026-06-02T10:00:00Z"),
            source = QuotaSnapshotSource.ManualRefresh,
            planType = null,
            windows = windows,
            credits = null,
            responseDigest = null,
        )
        return CurrentQuotaState(
            status = CurrentQuotaStatus.Fresh,
            freshness = CurrentQuotaFreshness.Fresh,
            account = account,
            snapshot = snapshot,
            latestAttempt = null,
            primaryWindow = windows.firstOrNull(),
            secondaryWindows = windows.drop(1),
            primaryWindowCanAlert = false,
            error = null,
        )
    }

    private fun percentWindow(id: String, usedPercent: Int) = QuotaWindow(
        windowId = QuotaWindowId(id), titleKey = id, usedPercent = usedPercent,
        resetAt = Instant.parse("2026-06-02T14:30:00Z"), limitWindowSeconds = null,
        isPrimaryCandidate = true, availability = QuotaWindowAvailability.Available,
        displayKind = QuotaWindowDisplayKind.Percent,
    )

    private fun usageCountWindow(id: String, usedPercent: Int, usedCount: Int, limitCount: Int) = QuotaWindow(
        windowId = QuotaWindowId(id), titleKey = id, usedPercent = usedPercent,
        resetAt = Instant.parse("2026-06-02T14:30:00Z"), limitWindowSeconds = null,
        isPrimaryCandidate = true, availability = QuotaWindowAvailability.Available,
        displayKind = QuotaWindowDisplayKind.UsageCount, usedCount = usedCount, limitCount = limitCount,
    )

    private fun balanceWindow(id: String, amount: String, currency: String) = QuotaWindow(
        windowId = QuotaWindowId(id), titleKey = id, usedPercent = null,
        resetAt = null, limitWindowSeconds = null, isPrimaryCandidate = false,
        availability = QuotaWindowAvailability.Available,
        displayKind = QuotaWindowDisplayKind.Balance, balanceAmount = amount, balanceCurrency = currency,
    )

    private fun balanceTone(amount: String, preferences: NotificationPreferences): WidgetQuotaTone {
        val state = freshState(windows = listOf(balanceWindow("balance", amount = amount, currency = "USD")))
        return factory.create(
            state,
            notificationPreferences = preferences,
            selectedWindowIds = listOf("balance"),
        ).fields.single().tone
    }
}
