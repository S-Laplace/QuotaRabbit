package com.alice.quotarabbit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class QuotaRabbitColorPalette(
    val isDark: Boolean,
    val primary: Color, val secondary: Color, val tertiary: Color,
    val neutral: Color, val neutralAlt: Color, val surface: Color, val surfaceSoft: Color,
    val border: Color, val accent: Color, val accentSoft: Color,
    val glassBase: Color, val glassInk: Color,
    val glassTintBlue: Color, val glassTintCyan: Color, val glassTintViolet: Color,
    val glassStrokeLight: Color, val glassStrokeCool: Color, val glassShadow: Color,
    val success: Color, val successSoft: Color,
    val warning: Color, val warningSoft: Color,
    val danger: Color, val dangerSoft: Color,
)

val LightQuotaRabbitColors = QuotaRabbitColorPalette(
    isDark = false,
    primary = Color(0xFF17181C), secondary = Color(0xFF5C6470), tertiary = Color(0xFF8A94A6),
    neutral = Color(0xFFF6F6F6), neutralAlt = Color(0xFFEEEEEE), surface = Color(0xFFFFFFFF), surfaceSoft = Color(0xFFF0F0F0),
    border = Color(0xFFDDDDDD), accent = Color(0xFF0063C7), accentSoft = Color(0xFFF8FBFF),
    glassBase = Color(0xFFEAF3FF), glassInk = Color(0xFF151821),
    glassTintBlue = Color(0xFF5DB8FF), glassTintCyan = Color(0xFF7EF4E8), glassTintViolet = Color(0xFF9D8CFF),
    glassStrokeLight = Color(0xFFFFFFFF), glassStrokeCool = Color(0xFFB9D8FF), glassShadow = Color(0xFF8AA9D6),
    success = Color(0xFF147D45), successSoft = Color(0xFFEAF7F0),
    warning = Color(0xFFB45309), warningSoft = Color(0xFFFFF4E4),
    danger = Color(0xFFB91C1C), dangerSoft = Color(0xFFFEEDEE),
)

val DarkQuotaRabbitColors = QuotaRabbitColorPalette(
    isDark = true,
    primary = Color(0xFFEEF1F6), secondary = Color(0xFF9AA4B2), tertiary = Color(0xFF6B7484),
    neutral = Color(0xFF181818), neutralAlt = Color(0xFF202020), surface = Color(0xFF242424), surfaceSoft = Color(0xFF202020),
    border = Color(0xFF3A3A3A), accent = Color(0xFF4C9EFF), accentSoft = Color(0xFF15314F),
    glassBase = Color(0xFF1C2636), glassInk = Color(0xFF151821),
    glassTintBlue = Color(0xFF5DB8FF), glassTintCyan = Color(0xFF7EF4E8), glassTintViolet = Color(0xFF9D8CFF),
    glassStrokeLight = Color(0xFFFFFFFF), glassStrokeCool = Color(0xFF3E5A7A), glassShadow = Color(0xFF060810),
    success = Color(0xFF35C77D), successSoft = Color(0xFF14271E),
    warning = Color(0xFFF0A63E), warningSoft = Color(0xFF2A2012),
    danger = Color(0xFFF2564B), dangerSoft = Color(0xFF2B1517),
)

internal val LocalQuotaRabbitColors = staticCompositionLocalOf { LightQuotaRabbitColors }

/** Same-name object + @Composable fun coexist (different namespaces), mirroring MaterialTheme. */
object QuotaRabbitTheme {
    val colors: QuotaRabbitColorPalette
        @Composable @ReadOnlyComposable get() = LocalQuotaRabbitColors.current
}
