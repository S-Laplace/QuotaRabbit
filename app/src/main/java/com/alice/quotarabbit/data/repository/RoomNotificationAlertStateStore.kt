package com.alice.quotarabbit.data.repository

import com.alice.quotarabbit.data.local.dao.AlertStateDao
import com.alice.quotarabbit.data.local.entity.AlertStateEntity
import com.alice.quotarabbit.notification.AlertDedupeKey
import com.alice.quotarabbit.notification.NotificationAlertStateStore
import com.alice.quotarabbit.notification.QuotaAlertEvent
import java.time.Instant

class RoomNotificationAlertStateStore(
    private val alertStateDao: AlertStateDao,
) : NotificationAlertStateStore {
    override suspend fun hasNotified(key: AlertDedupeKey): Boolean =
        alertStateDao.getByDedupeKey(
            providerId = key.providerId.value,
            localAccountId = key.localAccountId.value,
            windowId = key.windowId.value,
            resetAt = key.resetAt?.toString().orEmpty(),
            threshold = key.threshold,
        ) != null

    override suspend fun markNotified(event: QuotaAlertEvent, notifiedAt: Instant) {
        alertStateDao.upsert(
            AlertStateEntity(
                alertStateId = event.key.toAlertStateId(),
                providerId = event.key.providerId.value,
                localAccountId = event.key.localAccountId.value,
                windowId = event.key.windowId.value,
                threshold = event.key.threshold,
                resetAt = event.key.resetAt?.toString().orEmpty(),
                lastNotifiedAt = notifiedAt.toString(),
            ),
        )
    }

    private fun AlertDedupeKey.toAlertStateId(): String =
        listOf(
            providerId.value,
            localAccountId.value,
            windowId.value,
            (resetAt?.toEpochMilli()?.toString()).orEmpty(),
            threshold.toString(),
        ).joinToString(separator = "-")
}
