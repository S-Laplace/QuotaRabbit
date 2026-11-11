package com.alice.quotarabbit.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun formattedInstant(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    var zone by remember(context) { mutableStateOf(ZoneId.systemDefault()) }
    DisposableEffect(context) {
        // A timezone change does not require Activity recreation or a new quota snapshot.
        // Read the platform zone rather than trusting broadcast extras.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) zone = ZoneId.systemDefault()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return remember(instant, locale, zone) {
        formatHomeInstant(instant, locale, zone)
    }
}

/** Fixed year.month.day. and 24-hour time, always interpreted in the device's time zone. */
internal fun formatHomeInstant(instant: Instant, locale: Locale, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("yy.MM.dd. HH:mm", locale).withZone(zone).format(instant)

/** Display only; account renewal dates remain stored and edited as ISO yyyy-MM-dd. */
internal fun formatDisplayDateOnly(date: LocalDate): String =
    DateTimeFormatter.ofPattern("yy.MM.dd.", Locale.ROOT).format(date)

internal fun formatManualRenewalDate(isoDate: String): String =
    runCatching { formatDisplayDateOnly(LocalDate.parse(isoDate)) }.getOrDefault(isoDate)

@Composable
@ReadOnlyComposable
internal fun toneColor(tone: HomeStatusTone): Color =
    when (tone) {
        HomeStatusTone.Neutral -> QuotaRabbitTheme.colors.secondary
        HomeStatusTone.Success -> QuotaRabbitTheme.colors.success
        HomeStatusTone.Warning -> QuotaRabbitTheme.colors.warning
        HomeStatusTone.Danger -> QuotaRabbitTheme.colors.danger
    }
