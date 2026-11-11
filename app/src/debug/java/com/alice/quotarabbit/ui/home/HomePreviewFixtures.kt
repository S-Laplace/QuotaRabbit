package com.alice.quotarabbit.ui.home

import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.QuotaModelBucket
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.providers.ProviderRegistry
import java.time.Instant

/** Synthetic, display-safe local data only; no auth/session payload or API response fixtures. */
object HomePreviewFixtures {
    val metadataNow: Instant = Instant.parse("2026-10-10T12:00:00Z")

    fun state(scenario: String, now: Instant = Instant.now()): HomeUiState {
        if (scenario == "empty") return HomeUiState.unauthenticated()
        if (scenario == "loading") return HomeUiState.loading()
        if (scenario == "metadata") return metadataState(now)
        val codex = listOf(
            account("codex", "personal", "Personal", "Plus", now, 18, 44, isCurrent = true),
            account("codex", "work", "Work", "Pro", now, 38, 73),
            account("codex", "team", "Team", "Team", now, 94, 91),
        )
        val cards = when (scenario) {
            "mixed" -> listOf(codex.first()) + mixedAccounts(now)
            "states" -> listOf(
                codex[0],
                withStatus(codex[1], HomeContentStatus.ErrorWithLastKnownGood, now.minusSeconds(600)),
                withStatus(codex[2], HomeContentStatus.AuthRequired, now.minusSeconds(1200)),
                withStatus(account("codex", "stale", "Cached quota", null, now, 25, 35),
                    HomeContentStatus.PossiblyStale, now.minusSeconds(2700)),
                withStatus(account("codex", "expired", "Old snapshot", null, now, 40, 45),
                    HomeContentStatus.Expired, now.minusSeconds(10800)),
                withStatus(account("deepseek", "unknown", "No snapshot", null, now, 0, 0),
                    HomeContentStatus.NoData, null, keepQuota = false),
                withStatus(account("codex", "refreshing", "Refreshing account", null, now, 21, 48),
                    HomeContentStatus.Loading, now.minusSeconds(180)),
                account("codex", "disconnected", "Disconnected account", null, now, 21, 48)
                    .copy(isDisconnected = true),
            )
            "cached" -> codex.map {
                it.copy(state = it.state.copy(refresh = it.state.refresh.copy(
                    lastSuccessfulRefreshAt = now.minusSeconds(1200),
                )))
            }
            else -> codex
        }
        return cards.first().state.copy(accountCards = cards)
    }

    /** Unknown billing/banked fields stay absent; each account has its own real display facts. */
    private fun metadataState(now: Instant): HomeUiState {
        val successfulTimes = listOf(Instant.parse("2026-10-09T23:05:00Z"),
            Instant.parse("2026-10-10T00:15:00Z"), Instant.parse("2026-10-10T01:25:00Z"))
        val cards = listOf(
            account("codex", "personal", "Personal", "Plus", now, 18, 44, isCurrent = true),
            account("codex", "work", "Work", "Pro", now, 38, 73),
            account("codex", "team", "Team", "Team", now, 94, 91),
        ).mapIndexed { index, card ->
            val success = successfulTimes[index]
            val status = when (index) {
                1 -> HomeContentStatus.ErrorWithLastKnownGood
                2 -> HomeContentStatus.AuthRequired
                else -> HomeContentStatus.Fresh
            }
            val updated = withStatus(card, status, success)
            updated.copy(state = updated.state.copy(
                quotaCards = updated.state.quotaCards.mapIndexed { windowIndex, quota -> quota.copy(
                    resetAt = now.plusSeconds((index + 1) * 3600L + windowIndex * 172800L),
                ) },
                refresh = updated.state.refresh.copy(lastAttemptFinishedAt = success.plusSeconds(3600)),
            ))
        } + mixedAccounts(now) + withStatus(
            account("deepseek", "unknown", "No snapshot", null, now, 0, 0),
            HomeContentStatus.NoData, null, keepQuota = false,
        )
        return cards.first().state.copy(accountCards = cards)
    }

    private fun mixedAccounts(now: Instant): List<HomeDashboardAccountUi> {
        val balance = quota("balance", R.string.home_quota_window_balance).copy(
            displayKind = QuotaWindowDisplayKind.Balance, usedPercent = null,
            balanceAmount = "42.50", balanceCurrency = "USD", resetAt = null,
            grantedBalance = "10.00", toppedUpBalance = "32.50",
        )
        val count = quota("cursor_usage", R.string.home_quota_window_usage_count).copy(
            displayKind = QuotaWindowDisplayKind.UsageCount, usedPercent = null,
            usedCount = 125, limitCount = 500, resetAt = null,
        )
        val model = quota("model_quota", R.string.account_model_details_title).copy(
            displayKind = QuotaWindowDisplayKind.MultiModelFraction, usedPercent = null,
            status = HomeQuotaStatus.Unavailable, tone = HomeStatusTone.Neutral,
            statusLabelResId = R.string.home_quota_status_unavailable,
            resetAt = null, modelBuckets = listOf(
                QuotaModelBucket("model-a", "Model A", 0.82, now.plusSeconds(7200)),
                QuotaModelBucket("model-b", "Model B", 0.24, now.plusSeconds(10800)),
            ),
        )
        val unknown = quota("unknown_cycle", R.string.window_label_cycle).copy(
            usedPercent = null, resetAt = null, status = HomeQuotaStatus.Unavailable,
            tone = HomeStatusTone.Neutral, statusLabelResId = R.string.home_quota_status_unavailable,
        )
        return listOf(
            account("deepseek", "balance", "API balance", null, now, 0, 0, quotas = listOf(balance)),
            account("cursor", "requests", "Request account", "Pro", now, 0, 0, quotas = listOf(count)),
            account("antigravity", "models", "Model account", null, now, 0, 0, quotas = listOf(model)),
            account("kimi", "cycle", "Unknown quota", null, now, 0, 0, quotas = listOf(unknown)),
        )
    }

    private fun account(
        provider: String, id: String, name: String, plan: String?, now: Instant,
        used: Int, weeklyUsed: Int, isCurrent: Boolean = false,
        quotas: List<HomeQuotaCardUi> = listOf(
            quota("five_hour", R.string.home_quota_window_five_hour, used, now.plusSeconds(5400)),
            quota("weekly", R.string.home_quota_window_weekly, weeklyUsed, now.plusSeconds(172800)),
        ),
    ): HomeDashboardAccountUi {
        val identity = HomeAccountUi(
            displayName = name, avatarInitial = name.take(1), avatarColorKey = id,
            planType = plan, credits = HomeCreditsUi.Unavailable,
            providerIconResId = ProviderRegistry.iconFor(ProviderId(provider)),
        )
        val uiState = HomeUiState(
            contentStatus = HomeContentStatus.Fresh, statusTitleResId = R.string.home_state_fresh_title,
            statusDescriptionResId = R.string.home_state_fresh_description, errorMessageResId = null,
            account = identity, quotaCards = quotas, trend = HomeTrendUi(), loading = null,
            refresh = HomeRefreshUi(
                R.string.home_state_fresh_title, R.string.home_state_fresh_description,
                R.string.home_refresh, now.minusSeconds(120), now.minusSeconds(120),
            ),
            primaryAction = null, secondaryAction = null, isRefreshing = false,
        )
        return HomeDashboardAccountUi(
            ProviderId(provider), LocalAccountId("preview-$id"),
            ProviderRegistry.all.first { it.providerId.value == provider }.displayName, isCurrent, uiState,
            activityPoints = (1..48).map { offset ->
                HomeTrendPointUi(
                    capturedAt = now.minusSeconds(offset * 13_500L),
                    usageValue = if ((offset + id.length) % 4 == 0) 0.0
                        else ((offset * 7 + id.length) % 13 + 1).toDouble(),
                )
            },
        )
    }

    private fun quota(
        id: String, title: Int, used: Int = 0, resetAt: Instant? = null,
    ): HomeQuotaCardUi {
        val remaining = 100 - used
        val status = when {
            remaining <= 0 -> HomeQuotaStatus.Exhausted
            remaining <= 10 -> HomeQuotaStatus.Warning
            remaining <= 30 -> HomeQuotaStatus.Caution
            else -> HomeQuotaStatus.Normal
        }
        val label = when (status) {
            HomeQuotaStatus.Warning -> R.string.home_quota_status_warning
            HomeQuotaStatus.Caution -> R.string.home_quota_status_caution
            HomeQuotaStatus.Exhausted -> R.string.home_quota_status_exhausted
            else -> R.string.home_quota_status_normal
        }
        return HomeQuotaCardUi(
            id, title, QuotaWindowDisplayKind.Percent, used, null, null, null, null, null,
            true, resetAt, status,
            when (status) {
                HomeQuotaStatus.Normal -> HomeStatusTone.Success
                HomeQuotaStatus.Caution -> HomeStatusTone.Warning
                else -> HomeStatusTone.Danger
            }, label,
        )
    }

    private fun withStatus(
        card: HomeDashboardAccountUi, status: HomeContentStatus, fetchedAt: Instant?,
        keepQuota: Boolean = true,
    ): HomeDashboardAccountUi {
        val (title, description) = when (status) {
            HomeContentStatus.Fresh -> R.string.home_state_fresh_title to R.string.home_state_fresh_description
            HomeContentStatus.AuthRequired -> R.string.home_state_auth_required_title to R.string.home_state_auth_required_description
            HomeContentStatus.ErrorWithLastKnownGood -> R.string.home_state_error_lkg_title to R.string.home_state_error_lkg_description
            HomeContentStatus.PossiblyStale -> R.string.home_state_possibly_stale_title to R.string.home_state_possibly_stale_description
            HomeContentStatus.Expired -> R.string.home_state_expired_title to R.string.home_state_expired_description
            HomeContentStatus.Loading -> R.string.home_state_loading_title to R.string.home_state_loading_description
            else -> R.string.home_state_no_data_title to R.string.home_state_no_data_description
        }
        return card.copy(state = card.state.copy(
            contentStatus = status, statusTitleResId = title, statusDescriptionResId = description,
            errorMessageResId = if (status == HomeContentStatus.ErrorWithLastKnownGood) R.string.error_network else null,
            quotaCards = if (keepQuota) card.state.quotaCards else emptyList(),
            refresh = card.state.refresh.copy(titleResId = title, descriptionResId = description,
                lastSuccessfulRefreshAt = fetchedAt),
            isRefreshing = status == HomeContentStatus.Loading,
        ))
    }
}
