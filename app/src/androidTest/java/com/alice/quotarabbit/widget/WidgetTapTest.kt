package com.alice.quotarabbit.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.MainActivity
import com.alice.quotarabbit.ui.navigation.QuotaRabbitRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Sends the real widget PendingIntent on the offline fixture emulator, without seeding accounts. */
@RunWith(AndroidJUnit4::class)
class WidgetTapTest {
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun multiAccountWidgetTapOpensHome() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val spec = WidgetPreviewFixtures.spec("multi_4x3")
        val size = DpSize(300.dp, 250.dp)
        val remoteViews = GlanceRemoteViews().compose(context, size) {
            QuotaRabbitWidgetContent(context, spec.state, size, spec.variant)
        }.remoteViews
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync {
                val view = remoteViews.apply(context, FrameLayout(context))
                assertTrue("Widget must expose a tap target", requireNotNull(clickTarget(view)).performClick())
            }
            val activity = monitor.waitForActivityWithTimeout(5_000)
            assertNotNull("The widget PendingIntent must open the app", activity)
            try {
                assertEquals("home", requireNotNull(activity).intent.getStringExtra(QuotaRabbitRoute.EXTRA_LAUNCH_DESTINATION))
            } finally {
                activity?.let { instrumentation.runOnMainSync { it.finish() } }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun clickTarget(view: View): View? = when {
        view.isClickable -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { clickTarget(view.getChildAt(it)) }
        else -> null
    }
}
