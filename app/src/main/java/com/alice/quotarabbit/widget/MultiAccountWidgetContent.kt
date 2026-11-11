package com.alice.quotarabbit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.alice.quotarabbit.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Account rows adapt to host height and font scale using persisted local projections only. */
@Composable
internal fun MultiAccountWidgetContent(context: Context, accounts: List<WidgetAccountState>, size: DpSize) {
    val visible = accounts.take(widgetAccountCapacity(size, context.resources.configuration.fontScale))
    val hidden = accounts.size - visible.size
    val compact = size.height < 110.dp
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        visible.forEachIndexed { index, account ->
            AccountRow(context, account, GlanceModifier.fillMaxWidth().defaultWeight(), compact, hidden)
            if (index < visible.lastIndex) {
                Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(Divider)) {}
            }
        }
        if (hidden > 0 && !compact) {
            Text(
                text = context.getString(R.string.widget_multi_more, hidden),
                modifier = GlanceModifier.padding(top = 2.dp),
                style = TextStyle(color = MutedInk, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AccountRow(
    context: Context, account: WidgetAccountState, modifier: GlanceModifier, compact: Boolean, hidden: Int,
) {
    val state = account.asQuotaState()
    val statusText = context.getString(state.statusLabelResId())
    val field = account.fields.firstOrNull()
    // A 60dp host has room for two lines. Keep scaled text readable at the largest system font.
    val compactTextSize = if (context.resources.configuration.fontScale > 1.5f) 9 else 11
    val statusTextSize = if (compact) minOf(compactTextSize, 10) else 10
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Box(modifier = GlanceModifier.width(3.dp).height(14.dp).background(account.tone.statusAccentProvider())) {}
            Spacer(GlanceModifier.width(4.dp))
            account.providerIconRes?.let { icon ->
                Image(
                    provider = ImageProvider(icon), contentDescription = account.providerName,
                    modifier = GlanceModifier.width(14.dp).height(14.dp), colorFilter = ColorFilter.tint(Ink),
                )
                Spacer(GlanceModifier.width(4.dp))
            }
            Text(
                text = if (compact) account.accountName ?: context.getString(R.string.widget_multi_missing_account)
                    else context.getString(R.string.widget_multi_identity, account.providerName,
                        account.accountName ?: context.getString(R.string.widget_multi_missing_account)),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = Ink, fontSize = (if (compact) compactTextSize else 12).sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (compact) {
                Spacer(GlanceModifier.width(4.dp))
                Text(text = statusText, style = TextStyle(color = account.tone.statusAccentProvider(), fontSize = statusTextSize.sp), maxLines = 1)
            }
        }
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 1.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
            if (!compact) {
                Text(
                    text = field?.widgetTitle(context).orEmpty(),
                    modifier = GlanceModifier.defaultWeight().padding(end = 4.dp),
                    style = TextStyle(color = MutedInk, fontSize = 10.sp), maxLines = 1,
                )
            }
            Text(
                text = field?.widgetValue(context) ?: context.getString(R.string.widget_percent_unavailable),
                modifier = if (compact) GlanceModifier.defaultWeight() else GlanceModifier,
                style = TextStyle(color = Ink, fontSize = (if (compact) compactTextSize else 11).sp, fontWeight = FontWeight.Medium), maxLines = 1,
            )
            if (compact && hidden > 0) {
                Text(
                    text = context.getString(R.string.widget_multi_more_compact, hidden),
                    modifier = GlanceModifier.padding(start = 4.dp).semantics {
                        contentDescription = context.getString(R.string.widget_multi_more, hidden)
                    },
                    style = TextStyle(color = MutedInk, fontSize = 10.sp), maxLines = 1,
                )
            }
        }
        if (!compact) {
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 1.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(text = statusText, style = TextStyle(color = account.tone.statusAccentProvider(), fontSize = 10.sp), maxLines = 1)
                account.lastSuccessfulRefreshAt?.let {
                    val updated = context.getString(R.string.widget_multi_refreshed,
                        it.formatShortDateTime(context))
                    Text(
                        text = updated, modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
                        style = TextStyle(color = MutedInk, fontSize = 10.sp, textAlign = TextAlign.End), maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun WidgetAccountState.asQuotaState() = WidgetQuotaState(
    status, providerName, providerId, localAccountId, accountName, tone, WidgetClickTarget.Home,
    fields = fields,
)

private val Ink = ColorProvider(day = Color(0xFF151821), night = Color.White)
private val MutedInk = ColorProvider(day = Color(0xFF525A68), night = Color(0xFFCFD6E2))
private val Divider = ColorProvider(day = Color(0x226A86B0), night = Color(0x33B9D8FF))
