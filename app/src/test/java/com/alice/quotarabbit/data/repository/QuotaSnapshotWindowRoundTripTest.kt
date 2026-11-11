package com.alice.quotarabbit.data.repository

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.model.SnapshotId
import com.alice.quotarabbit.domain.quota.QuotaModelBucket
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaSnapshotWindowRoundTripTest {
    private fun snapshot(windows: List<QuotaWindow>) = QuotaSnapshot(
        snapshotId = SnapshotId("snap"),
        providerId = ProviderId("p"),
        localAccountId = LocalAccountId("a"),
        providerAccountId = ProviderAccountId("pa"),
        fetchedAt = Instant.parse("2026-05-31T00:00:00Z"),
        source = QuotaSnapshotSource.ManualRefresh,
        planType = "pro",
        windows = windows,
        credits = null,
        responseDigest = null,
    )

    @Test
    fun codexBenefits_surviveEntityRoundTripWithoutFabricatingMissingValues() {
        val benefits = com.alice.quotarabbit.domain.quota.CodexBenefits(
            bankedResetCount = 2,
            subscriptionRenewsAt = Instant.parse("2026-11-01T12:00:00Z"),
            subscriptionActiveUntil = Instant.parse("2026-11-12T16:00:00Z"),
            activeUntilFromToken = true,
        )
        val original = snapshot(emptyList()).copy(codexBenefits = benefits)
        val entity = original.toEntity()
        assertEquals(benefits, entity.toDomain().codexBenefits)
        assertEquals(null, snapshot(emptyList()).toEntity().toDomain().codexBenefits)

        // Existing v3 rows written by previous APKs must decode without the new JSON keys.
        val existing = snapshot(emptyList()).toEntity().copy(
            providerBenefitsJson = """{"bankedResetCount":2,"subscriptionRenewsAtMs":1793534400000}""",
        ).toDomain().codexBenefits
        assertEquals(2, existing?.bankedResetCount)
        assertEquals(null, existing?.subscriptionActiveUntil)
        assertEquals(false, existing?.activeUntilFromToken)
    }

    @Test
    fun balanceWindow_survivesEntityRoundTrip() {
        val window = QuotaWindow(
            windowId = QuotaWindowId("balance"),
            titleKey = "deepseek_balance",
            usedPercent = null,
            resetAt = null,
            limitWindowSeconds = null,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
            displayKind = QuotaWindowDisplayKind.Balance,
            balanceAmount = "9.49",
            balanceCurrency = "CNY",
            subLabel = "Granted 0 · Topped up 9.49",
        )

        val restored = snapshot(listOf(window)).toEntity().toDomain().windows.single()

        assertEquals(QuotaWindowDisplayKind.Balance, restored.displayKind)
        assertEquals("9.49", restored.balanceAmount)
        assertEquals("CNY", restored.balanceCurrency)
        assertEquals("Granted 0 · Topped up 9.49", restored.subLabel)
    }

    @Test
    fun usageCountWindow_survivesEntityRoundTrip() {
        val window = QuotaWindow(
            windowId = QuotaWindowId("cursor_plan"),
            titleKey = "cursor_plan_window",
            usedPercent = 64,
            resetAt = null,
            limitWindowSeconds = null,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
            displayKind = QuotaWindowDisplayKind.UsageCount,
            usedCount = 320,
            limitCount = 500,
        )

        val restored = snapshot(listOf(window)).toEntity().toDomain().windows.single()

        assertEquals(QuotaWindowDisplayKind.UsageCount, restored.displayKind)
        assertEquals(320, restored.usedCount)
        assertEquals(500, restored.limitCount)
    }

    @Test
    fun multiModelBuckets_surviveEntityRoundTrip() {
        val window = QuotaWindow(
            windowId = QuotaWindowId("antigravity_claude_window"),
            titleKey = "antigravity_claude_window",
            usedPercent = 15,
            resetAt = null,
            limitWindowSeconds = null,
            isPrimaryCandidate = true,
            availability = QuotaWindowAvailability.Available,
            displayKind = QuotaWindowDisplayKind.MultiModelFraction,
            modelBuckets = listOf(
                QuotaModelBucket("claude-sonnet-4-6", "Claude Sonnet 4.6", 0.92, Instant.parse("2026-06-01T00:00:00Z")),
                QuotaModelBucket("gpt-oss-120b-medium", "GPT-OSS 120B (Medium)", 0.6, null),
            ),
        )

        val restored = snapshot(listOf(window)).toEntity().toDomain().windows.single()

        assertEquals(QuotaWindowDisplayKind.MultiModelFraction, restored.displayKind)
        assertEquals(2, restored.modelBuckets.size)
        assertEquals("Claude Sonnet 4.6", restored.modelBuckets[0].displayName)
        assertEquals(0.92, restored.modelBuckets[0].remainingFraction, 0.0001)
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), restored.modelBuckets[0].resetAt)
        assertEquals(null, restored.modelBuckets[1].resetAt)
    }
}
