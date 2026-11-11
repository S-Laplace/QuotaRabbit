package com.alice.quotarabbit.ui.typography

import com.alice.quotarabbit.domain.account.AccountListResult
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.model.SnapshotId
import com.alice.quotarabbit.domain.quota.CurrentQuotaFreshness
import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.quota.CurrentQuotaStatus
import com.alice.quotarabbit.domain.quota.HomeDashboardState
import com.alice.quotarabbit.domain.quota.QuotaModelBucket
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.quota.QuotaWindow
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import java.time.Instant

/** Display facts only. These specimens never contain credentials, DTOs, or persisted account data. */
object TypographyPreviewFixtures {
    val accountIds = listOf("typo-latin", "typo-korean", "typo-chinese", "typo-models", "typo-counts", "typo-balance")
    val accountNames = listOf(
        "Personal development workspace",
        "개발 프로젝트 개인 계정",
        "团队研发工作账号",
        "Models · 추론 · 推理",
        "Requests · 요청 · 请求",
        "Balance · 잔액 · 余额",
    )

    fun dashboard(now: Instant = Instant.now()): HomeDashboardState {
        val codex = (0..2).map { index ->
            accountState(
                index, "codex", now, listOf(
                    percent("five_hour", 18 + index * 20, now.plusSeconds(5400), 18_000),
                    percent("weekly", 44 + index * 15, now.plusSeconds(172_800), 604_800),
                ),
            )
        }
        val models = accountState(3, "antigravity", now, listOf(
            percent("model_quota", 18, null, null).copy(
                displayKind = QuotaWindowDisplayKind.MultiModelFraction,
                modelBuckets = listOf(
                    QuotaModelBucket("claude_reasoning", "Reasoning · 추론 모델 · 推理模型", 0.82, now.plusSeconds(7200)),
                    QuotaModelBucket("gemini_pro_coding", "Coding · 코딩 모델 · 编程模型", 0.24, now.plusSeconds(10800)),
                ),
            ),
        ))
        val counts = accountState(4, "cursor", now, listOf(
            percent("cursor_plan", null, now.plusSeconds(3600), null).copy(
                displayKind = QuotaWindowDisplayKind.UsageCount, usedCount = 1250, limitCount = 50_000,
            ),
        ))
        val balance = accountState(5, "deepseek", now, listOf(
            percent("balance", null, null, null).copy(
                displayKind = QuotaWindowDisplayKind.Balance, balanceAmount = "12345.67", balanceCurrency = "USD",
                grantedBalance = "2345.67", toppedUpBalance = "10000.00",
            ),
        ))
        return HomeDashboardState(codex.first(), codex + listOf(models, counts, balance))
    }

    fun accounts(dashboard: HomeDashboardState): AccountListResult = AccountListResult(
        accounts = dashboard.accountStates.mapNotNull { it.account },
        currentAccountId = dashboard.currentState.account?.localAccountId,
        latestQuotaSnapshots = dashboard.accountStates.mapNotNull { state ->
            state.snapshot?.let { it.localAccountId to it }
        }.toMap(),
    )

    private fun accountState(index: Int, provider: String, now: Instant, windows: List<QuotaWindow>): CurrentQuotaState {
        val account = ProviderAccount.createNew(
            localAccountId = LocalAccountId(accountIds[index]), providerId = ProviderId(provider),
            providerAccountId = null, displayName = accountNames[index], now = now.minusSeconds(index.toLong()),
        ).copy(lastSuccessfulRefreshAt = now.minusSeconds(120))
        val snapshot = QuotaSnapshot(
            snapshotId = SnapshotId("typography-${accountIds[index]}"), providerId = account.providerId,
            localAccountId = account.localAccountId, providerAccountId = null,
            fetchedAt = now.minusSeconds(120), source = QuotaSnapshotSource.ManualRefresh,
            planType = if (provider == "codex") "Plus" else null,
            windows = windows, credits = null, responseDigest = null,
        )
        return CurrentQuotaState(
            status = CurrentQuotaStatus.Fresh, freshness = CurrentQuotaFreshness.Fresh,
            account = account, snapshot = snapshot, latestAttempt = null,
            primaryWindow = windows.first(), secondaryWindows = windows.drop(1),
            primaryWindowCanAlert = false, error = null,
        )
    }

    private fun percent(id: String, used: Int?, resetAt: Instant?, duration: Int?): QuotaWindow = QuotaWindow(
        windowId = QuotaWindowId(id), titleKey = "quota_window_$id", usedPercent = used,
        resetAt = resetAt, limitWindowSeconds = duration, isPrimaryCandidate = true,
        availability = QuotaWindowAvailability.Available,
    )
}
