package com.alice.quotarabbit.app

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.alice.quotarabbit.widget.QuotaRabbitWidget

fun interface WidgetCountReader {
    suspend fun configuredWidgetCount(): Int?
}

internal class GlanceWidgetCountReader(
    context: Context,
) : WidgetCountReader {
    private val appContext = context.applicationContext

    override suspend fun configuredWidgetCount(): Int? =
        runCatching {
            GlanceAppWidgetManager(appContext).getGlanceIds(QuotaRabbitWidget::class.java).size
        }.getOrNull()
}
