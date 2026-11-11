package com.alice.quotarabbit.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alice.quotarabbit.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Real Glance RemoteViews rendering with fake data only, including resized multi-account widgets. */
@RunWith(AndroidJUnit4::class)
class WidgetScreenshotTest {
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun renderWidgetSizes() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val outDir = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "quota-ui-widgets")
        assertTrue(outDir.exists() || outDir.mkdirs())
        for (locale in listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)) {
            for (night in listOf(false, true)) {
                for (scale in listOf(1f, 1.5f, 2f)) {
                    val configuration = Configuration(appContext.resources.configuration).apply {
                        setLocale(locale)
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        fontScale = scale
                    }
                    val context = appContext.createConfigurationContext(configuration)
                    val density = context.resources.displayMetrics.density
                    val glance = GlanceRemoteViews()
                    for (spec in WidgetPreviewFixtures.specs()) {
                        if (scale > 1.5f && !spec.name.startsWith("multi_")) continue
                        val size = DpSize(spec.widthDp.dp, spec.heightDp.dp)
                        val remoteViews = glance.compose(context, size) {
                            QuotaRabbitWidgetContent(context, spec.state, size, spec.variant)
                        }.remoteViews
                        val view = remoteViews.apply(context, FrameLayout(context))
                        val width = (spec.widthDp * density).toInt()
                        val height = (spec.heightDp * density).toInt()
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                        )
                        view.layout(0, 0, width, height)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(if (night) Color.rgb(48, 52, 60) else Color.rgb(214, 225, 235))
                        view.draw(canvas)
                        val theme = if (night) "dark" else "light"
                        val language = if (locale.language == "en") "" else "zh_"
                        FileOutputStream(File(outDir, "widget_${language}${spec.name}_${theme}_${scale}.png")).use {
                            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                        bitmap.recycle()
                        verifyMultiAccountContent(spec, view, context, size)
                    }
                }
            }
        }
    }

    private fun verifyMultiAccountContent(spec: WidgetPreviewSpec, view: View, context: Context, size: DpSize) {
        if (!spec.name.startsWith("multi_")) return
        val texts = textViews(view)
        val combined = texts.joinToString("\n") { it.text.toString() }
        val visible = spec.state.accounts.take(widgetAccountCapacity(size, context.resources.configuration.fontScale))
        for (account in visible) {
            val name = requireNotNull(account.accountName)
            assertTrue("${spec.name} is missing $name", combined.contains(name))
            val identity = texts.first { it.text.toString().contains(name) }
            assertVisibleLine(spec.name, identity, allowEllipsis = true)
            val state = WidgetQuotaState(account.status, account.providerName, account.providerId,
                account.localAccountId, name, account.tone, WidgetClickTarget.Home, fields = account.fields)
            val status = context.getString(state.statusLabelResId())
            // Walk in render order so accounts with the same status cannot mask a clipped row.
            val afterIdentity = texts.drop(texts.indexOf(identity) + 1)
            val statusView = afterIdentity.first { it.text.toString().startsWith(status) }
            assertVisibleLine("${spec.name}: $name status", statusView)
            account.fields.firstOrNull()?.let { field ->
                val value = field.widgetValue(context)
                val valueView = afterIdentity.first { it.text.toString().contains(value) }
                assertVisibleLine("${spec.name}: $name quota", valueView)
            }
        }
        val hidden = spec.state.accounts.size - visible.size
        if (hidden > 0) assertTrue("Hidden accounts must be disclosed",
            combined.contains(context.getString(R.string.widget_multi_more, hidden)) ||
                combined.contains(context.getString(R.string.widget_multi_more_compact, hidden)))
        if (spec.name == "multi_4x3" && visible.size == 4) {
            assertTrue("Balance must retain its currency/value", combined.contains("42.50"))
        }
        if (spec.name == "multi_counts") {
            assertTrue("Count quota must show actual counts", combined.contains("125") && combined.contains("500"))
        }
    }

    private fun assertVisibleLine(name: String, text: TextView, allowEllipsis: Boolean = false) {
        val layout = requireNotNull(text.layout) { "$name has no text layout" }
        assertTrue("$name has no visible space", text.width > 0 && text.height > 0)
        assertTrue("$name has clipped text: height=${text.height}, lineBottom=${layout.getLineBottom(layout.lineCount - 1)}, padding=${text.totalPaddingTop}/${text.totalPaddingBottom}",
            layout.getLineBottom(layout.lineCount - 1) <= text.height - text.totalPaddingTop - text.totalPaddingBottom)
        if (!allowEllipsis) assertTrue("$name truncates its value/status", layout.getEllipsisCount(0) == 0)
        var child: View = text
        var parent = child.parent
        while (parent is ViewGroup) {
            assertTrue("$name extends below its parent", child.top >= 0 && child.bottom <= parent.height)
            child = parent
            parent = child.parent
        }
    }

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
