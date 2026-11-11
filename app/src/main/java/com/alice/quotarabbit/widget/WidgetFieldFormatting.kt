package com.alice.quotarabbit.widget

import android.content.Context
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.ui.quota.formatProviderBalance
import com.alice.quotarabbit.ui.quota.quotaWindowLabelRes

internal fun WidgetField.widgetTitle(context: Context): String =
    modelName ?: context.getString(quotaWindowLabelRes(windowId, limitWindowSeconds))

internal fun WidgetField.widgetValue(context: Context): String = when (displayKind) {
    QuotaWindowDisplayKind.Balance -> formatProviderBalance(balanceAmount.finiteWidgetBalanceAmount(), balanceCurrency)
    QuotaWindowDisplayKind.UsageCount -> usedCount?.let { used ->
        limitCount?.let { limit -> context.getString(R.string.widget_multi_count_used, used, limit) }
            ?: context.getString(R.string.widget_multi_count_used_without_limit, used)
    }
    QuotaWindowDisplayKind.Percent,
    QuotaWindowDisplayKind.MultiModelFraction -> percent?.let {
        context.getString(R.string.widget_multi_percent_remaining, it)
    }
} ?: context.getString(R.string.widget_percent_unavailable)
