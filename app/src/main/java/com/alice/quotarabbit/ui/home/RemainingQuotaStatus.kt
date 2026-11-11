package com.alice.quotarabbit.ui.home

import com.alice.quotarabbit.domain.settings.NotificationPreferences

/** One percentage severity policy for Home cards and Account quota gauges. */
internal fun remainingQuotaStatus(percent: Double?, prefs: NotificationPreferences): HomeQuotaStatus {
    if (percent == null || !percent.isFinite()) return HomeQuotaStatus.Unavailable
    return when {
        percent <= prefs.limitThreshold -> HomeQuotaStatus.Exhausted
        percent <= prefs.warningThreshold -> HomeQuotaStatus.Warning
        percent <= prefs.cautionThreshold -> HomeQuotaStatus.Caution
        else -> HomeQuotaStatus.Normal
    }
}
