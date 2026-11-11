package com.alice.quotarabbit.ui.theme

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Suppress("DEPRECATION") // Opaque system bar colors are still needed on Android 12–14.
@Composable
fun QuotaRabbitSystemBars(window: Window) {
    val colors = QuotaRabbitTheme.colors
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val backgroundColor = colors.neutral.toArgb()
        window.setBackgroundDrawable(ColorDrawable(backgroundColor))
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        window.navigationBarDividerColor = Color.TRANSPARENT

        // Android 15+ draws behind the bars; the existing page backdrop supplies their color.
        val barColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            Color.TRANSPARENT
        } else {
            backgroundColor
        }
        window.statusBarColor = barColor
        window.navigationBarColor = barColor

        updateSystemBarIcons(window, colors.isDark)
    }
}

/** Call inside dialog content so its separate window follows the app's resolved appearance. */
@Composable
fun QuotaRabbitDialogSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val isDark = QuotaRabbitTheme.colors.isDark
    SideEffect {
        // Keep the dialog's transparent background, native dim and existing bar colors intact.
        updateSystemBarIcons(window, isDark)
    }
}

private fun updateSystemBarIcons(window: Window, isDark: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !isDark
        isAppearanceLightNavigationBars = !isDark
    }
}
