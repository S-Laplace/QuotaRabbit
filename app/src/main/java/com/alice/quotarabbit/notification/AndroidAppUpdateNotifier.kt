package com.alice.quotarabbit.notification

import com.alice.quotarabbit.domain.update.AppUpdateInfo
import com.alice.quotarabbit.domain.update.AppUpdateNotifier

class AndroidAppUpdateNotifier(
    private val notificationSink: NotificationSink,
    private val orchestrator: AppUpdateNotificationOrchestrator = AppUpdateNotificationOrchestrator(),
) : AppUpdateNotifier {
    override fun notifyUpdateAvailable(update: AppUpdateInfo) {
        notificationSink.post(orchestrator.updateAvailable(update))
    }
}
