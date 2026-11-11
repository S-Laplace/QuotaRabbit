package com.alice.quotarabbit.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.alice.quotarabbit.domain.quota.CurrentQuotaState
import com.alice.quotarabbit.domain.settings.NotificationPreferenceReader
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.refresh.CurrentQuotaStatePublisher

class WidgetQuotaStateUpdater(
    context: Context,
    private val factory: WidgetQuotaStateFactory = WidgetQuotaStateFactory(),
    private val widget: QuotaRabbitWidget = QuotaRabbitWidget(),
    private val notificationPreferenceReader: NotificationPreferenceReader =
        DefaultWidgetNotificationPreferenceReader,
    private val widgetQuotaStateLoader: WidgetQuotaStateLoader? = null,
) : CurrentQuotaStatePublisher {
    private val appContext = context.applicationContext

    override suspend fun publish(state: CurrentQuotaState) {
        val notificationPreferences = notificationPreferenceReader.notificationPreferences()
        val manager = GlanceAppWidgetManager(appContext)
        manager.getGlanceIds(QuotaRabbitWidget::class.java).forEach { glanceId ->
            updateAppWidgetState(appContext, glanceId) { preferences ->
                val configuration = preferences.toWidgetQuotaConfiguration()
                val configuredWidgetState = widgetQuotaStateLoader?.loadWidgetQuotaState(configuration)
                    ?: factory.create(
                        state,
                        notificationPreferences,
                        configuration.selectedWindowIds,
                    )
                preferences.writeWidgetQuotaState(configuredWidgetState)
            }
        }
        widget.updateAll(appContext)
    }
}

private object DefaultWidgetNotificationPreferenceReader : NotificationPreferenceReader {
    override suspend fun notificationPreferences(): NotificationPreferences = NotificationPreferences()
}
