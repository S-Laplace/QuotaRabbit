package com.alice.quotarabbit.ui.home

import com.alice.quotarabbit.domain.settings.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingQuotaStatusTest {
    @Test fun defaultThresholdsMatchHomeStatusPolicy() {
        val prefs = NotificationPreferences()
        assertEquals(HomeQuotaStatus.Normal, remainingQuotaStatus(100.0, prefs))
        assertEquals(HomeQuotaStatus.Normal, remainingQuotaStatus(31.0, prefs))
        assertEquals(HomeQuotaStatus.Caution, remainingQuotaStatus(30.0, prefs))
        assertEquals(HomeQuotaStatus.Caution, remainingQuotaStatus(11.0, prefs))
        assertEquals(HomeQuotaStatus.Warning, remainingQuotaStatus(10.0, prefs))
        assertEquals(HomeQuotaStatus.Warning, remainingQuotaStatus(1.0, prefs))
        assertEquals(HomeQuotaStatus.Exhausted, remainingQuotaStatus(0.0, prefs))
        assertEquals(HomeQuotaStatus.Unavailable, remainingQuotaStatus(null, prefs))
        assertEquals(HomeQuotaStatus.Unavailable, remainingQuotaStatus(Double.NaN, prefs))
    }

    @Test fun customizedThresholdsAreNotFixedAtOldAccountValues() {
        val prefs = NotificationPreferences(cautionThreshold = 65, warningThreshold = 23)
        assertEquals(HomeQuotaStatus.Normal, remainingQuotaStatus(66.0, prefs))
        assertEquals(HomeQuotaStatus.Caution, remainingQuotaStatus(65.0, prefs))
        assertEquals(HomeQuotaStatus.Warning, remainingQuotaStatus(23.0, prefs))
        assertEquals(HomeQuotaStatus.Exhausted, remainingQuotaStatus(-1.0, prefs))
    }
}
