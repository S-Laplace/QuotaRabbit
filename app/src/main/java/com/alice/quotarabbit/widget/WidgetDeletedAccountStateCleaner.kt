package com.alice.quotarabbit.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.alice.quotarabbit.domain.account.DeletedAccountStateCleanup
import com.alice.quotarabbit.domain.account.DeletedAccountStateCleaner
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId

class WidgetDeletedAccountStateCleaner(
    context: Context,
    private val widget: QuotaRabbitWidget = QuotaRabbitWidget(),
) : DeletedAccountStateCleaner {
    private val appContext = context.applicationContext

    override suspend fun clearDeletedAccountState(
        providerId: ProviderId,
        localAccountId: LocalAccountId,
    ): DeletedAccountStateCleanup {
        val clearedStates = mutableListOf<WidgetStateSnapshot>()
        val manager = GlanceAppWidgetManager(appContext)
        manager.getGlanceIds(QuotaRabbitWidget::class.java).forEach { glanceId ->
            updateAppWidgetState(appContext, glanceId) { preferences ->
                val previousState = preferences.toWidgetQuotaState()
                val previousConfiguration = preferences.toWidgetQuotaConfiguration()
                val changed = preferences.clearWidgetQuotaStateIfAccountMatches(
                    providerId = providerId.value,
                    localAccountId = localAccountId.value,
                )
                if (changed) {
                    clearedStates += WidgetStateSnapshot(glanceId, previousState, previousConfiguration)
                }
            }
        }
        if (clearedStates.isNotEmpty()) {
            widget.updateAll(appContext)
        }
        return WidgetDeletedAccountStateCleanup(
            appContext = appContext,
            snapshots = clearedStates,
            widget = widget,
        )
    }
}

private data class WidgetStateSnapshot(
    val glanceId: GlanceId,
    val state: WidgetQuotaState,
    val configuration: WidgetQuotaConfiguration,
)

private class WidgetDeletedAccountStateCleanup(
    private val appContext: Context,
    private val snapshots: List<WidgetStateSnapshot>,
    private val widget: QuotaRabbitWidget,
) : DeletedAccountStateCleanup {
    override suspend fun restore() {
        snapshots.forEach { snapshot ->
            updateAppWidgetState(appContext, snapshot.glanceId) { preferences ->
                preferences.writeWidgetQuotaState(snapshot.state)
                preferences.writeWidgetQuotaConfiguration(snapshot.configuration)
            }
        }
        if (snapshots.isNotEmpty()) {
            widget.updateAll(appContext)
        }
    }
}
