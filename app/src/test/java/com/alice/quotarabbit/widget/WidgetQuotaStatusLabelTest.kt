package com.alice.quotarabbit.widget

import com.alice.quotarabbit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetQuotaStatusLabelTest {
    @Test
    fun `legacy fresh invalid balance is unavailable regardless saved tone`() {
        for (amount in listOf(null, "N/A", "NaN", "Infinity", "-Infinity", "1e9999")) {
            for (tone in WidgetQuotaTone.entries) {
                val state = freshState(tone).copy(fields = listOf(balanceField().copy(balanceAmount = amount)))
                assertEquals(R.string.home_quota_status_unavailable, state.statusLabelResId())
            }
        }
    }

    @Test
    fun `verified zero and negative balance remain exhausted even with old healthy tone`() {
        for (amount in listOf("0.00", "-0.01", "-500")) {
            val state = freshState(WidgetQuotaTone.Success).copy(fields = listOf(balanceField().copy(balanceAmount = amount)))
            assertEquals(R.string.home_quota_status_exhausted, state.statusLabelResId())
        }
    }

    @Test
    fun `known zero quota and balance have exhausted copy instead of tight copy`() {
        val percent = freshState(WidgetQuotaTone.Danger).copy(fields = listOf(
            WidgetField("five_hour", false, 0, null, null, null, WidgetQuotaTone.Danger),
        ))
        val balance = freshState(WidgetQuotaTone.Danger).copy(fields = listOf(
            WidgetField("balance", true, null, "0.00", "USD", null, WidgetQuotaTone.Danger),
        ))
        assertEquals(R.string.home_quota_status_exhausted, percent.statusLabelResId())
        assertEquals(R.string.home_quota_status_exhausted, balance.statusLabelResId())
    }

    @Test
    fun `verified request counts without percentage have data fresh copy`() {
        val state = freshState(WidgetQuotaTone.Neutral).copy(fields = listOf(
            WidgetField("requests", false, null, null, null, null, WidgetQuotaTone.Neutral,
                displayKind = com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind.UsageCount,
                usedCount = 12, limitCount = 100),
        ))
        assertEquals(R.string.home_state_fresh_title, state.statusLabelResId())
    }

    @Test
    fun `fresh widget label follows header tone`() {
        assertEquals(
            R.string.home_quota_status_normal,
            freshState(tone = WidgetQuotaTone.Success).statusLabelResId(),
        )
        assertEquals(
            R.string.home_quota_status_caution,
            freshState(tone = WidgetQuotaTone.Warning).statusLabelResId(),
        )
        assertEquals(
            R.string.home_quota_status_warning,
            freshState(tone = WidgetQuotaTone.Danger).statusLabelResId(),
        )
        assertEquals(
            R.string.home_quota_status_unavailable,
            freshState(tone = WidgetQuotaTone.Neutral).statusLabelResId(),
        )
    }

    @Test
    fun `fresh balance account follows tone status labels`() {
        // 余额阈值档位同步到微件后，余额账户与额度账户一样按 tone 出状态词
        // （正常 / 注意 / 紧张），不再固定显示特殊标签或误示「暂不可用」。
        assertEquals(
            R.string.home_quota_status_normal,
            freshState(tone = WidgetQuotaTone.Success).copy(fields = listOf(balanceField().copy(balanceAmount = "42.50"))).statusLabelResId(),
        )
        assertEquals(
            R.string.home_quota_status_warning,
            freshState(tone = WidgetQuotaTone.Danger).copy(fields = listOf(balanceField().copy(balanceAmount = "1.50"))).statusLabelResId(),
        )
    }

    @Test
    fun `fresh quota account with unreadable percent still reads unavailable`() {
        val state = freshState(tone = WidgetQuotaTone.Neutral)
            .copy(fields = listOf(balanceField(isBalance = false)))

        assertEquals(R.string.home_quota_status_unavailable, state.statusLabelResId())
    }

    @Test
    fun `non-fresh statuses map to dedicated labels`() {
        assertEquals(R.string.widget_connect_codex, stateWith(WidgetQuotaStatus.NoAccount).statusLabelResId())
        assertEquals(R.string.widget_status_possibly_stale, stateWith(WidgetQuotaStatus.PossiblyStale).statusLabelResId())
        assertEquals(R.string.widget_status_expired, stateWith(WidgetQuotaStatus.Expired).statusLabelResId())
        assertEquals(R.string.widget_status_auth_required, stateWith(WidgetQuotaStatus.AuthRequired).statusLabelResId())
        assertEquals(R.string.widget_status_refresh_failed, stateWith(WidgetQuotaStatus.ErrorWithLastKnownGood).statusLabelResId())
        assertEquals(R.string.widget_status_no_data, stateWith(WidgetQuotaStatus.NoData).statusLabelResId())
    }

    @Test
    fun `compact status rail glow stays narrow and uses semantic accent`() {
        val style = WidgetStatusGlowRenderer.styleSpec(WidgetQuotaTone.Success)

        assertEquals(0xFF5CF2A6.toInt(), style.accentArgb)
        assertEquals(0x335CF2A6, style.outerGlowArgb)
        assertTrue(style.outerGlowWidthDp in 6.5f..8.0f)
        assertTrue(style.innerGlowWidthDp in 4.0f..5.2f)
        assertEquals(3.0f, style.coreWidthDp)
        assertTrue(style.outerBlurRadiusDp <= 3.0f)
        assertTrue(style.innerBlurRadiusDp <= 1.8f)
        assertTrue(style.innerGlowCenterAlpha <= 82)
    }

    private fun freshState(tone: WidgetQuotaTone): WidgetQuotaState =
        stateWith(status = WidgetQuotaStatus.Fresh, tone = tone)

    private fun balanceField(isBalance: Boolean = true): WidgetField =
        WidgetField(
            windowId = "balance",
            isBalance = isBalance,
            percent = null,
            balanceAmount = null,
            balanceCurrency = null,
            resetAt = null,
            tone = WidgetQuotaTone.Neutral,
        )

    private fun stateWith(
        status: WidgetQuotaStatus,
        tone: WidgetQuotaTone = WidgetQuotaTone.Neutral,
    ): WidgetQuotaState =
        WidgetQuotaState(
            status = status,
            providerName = "Codex",
            providerId = "codex",
            localAccountId = "local-1",
            accountName = "Codex Main",
            tone = tone,
            clickTarget = WidgetClickTarget.Home,
        )
}
