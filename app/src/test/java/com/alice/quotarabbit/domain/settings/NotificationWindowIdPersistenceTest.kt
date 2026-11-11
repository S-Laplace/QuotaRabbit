package com.alice.quotarabbit.domain.settings

import com.alice.quotarabbit.domain.model.QuotaWindowId
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationWindowIdPersistenceTest {
    @Test
    fun `accepts a non-supported window id without throwing`() {
        val prefs = NotificationPreferences(persistentNotificationWindowId = QuotaWindowId("balance"))
        assertEquals(QuotaWindowId("balance"), prefs.persistentNotificationWindowId)
    }
}
