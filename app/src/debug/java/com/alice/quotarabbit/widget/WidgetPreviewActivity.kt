package com.alice.quotarabbit.widget

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Debug-only preview that lays the real widget content over the live wallpaper so the translucent
 * glass style can be visually accepted. Launch per spec:
 *   adb shell am start -n com.alice.quotarabbit.debug/com.alice.quotarabbit.widget.WidgetPreviewActivity --es spec 4x2
 * then `adb exec-out screencap -p`.
 */
class WidgetPreviewActivity : ComponentActivity() {

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        setContentView(root)

        val specName = intent?.getStringExtra("spec") ?: "multi_4x3"
        val spec = WidgetPreviewFixtures.spec(specName)

        lifecycleScope.launch {
            val glance = GlanceRemoteViews()
            val size = DpSize(spec.widthDp.dp, spec.heightDp.dp)
            val remoteViews = glance.compose(this@WidgetPreviewActivity, size) {
                QuotaRabbitWidgetContent(this@WidgetPreviewActivity, spec.state, size, spec.variant)
            }.remoteViews
            val container = FrameLayout(this@WidgetPreviewActivity)
            val view = remoteViews.apply(this@WidgetPreviewActivity, container)
            container.addView(view)
            root.addView(
                container,
                LinearLayout.LayoutParams(
                    (spec.widthDp * density).toInt(),
                    (spec.heightDp * density).toInt(),
                ),
            )
        }
    }

}
