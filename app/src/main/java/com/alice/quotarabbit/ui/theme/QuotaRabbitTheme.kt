package com.alice.quotarabbit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.domain.theme.resolveDarkAppearance

enum class QuotaRabbitFontScheme {
    SystemDefault,
    GeistHybrid,
    InterJetBrainsMono,
    MonoFocusGeistMono,
}

data class QuotaRabbitTextStyles(
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val number: TextStyle,
) {
    val material: Typography = Typography(
        displayLarge = display.copy(fontSize = 54.sp, lineHeight = 62.sp),
        displayMedium = display.copy(fontSize = 42.sp, lineHeight = 50.sp),
        displaySmall = display.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
        headlineLarge = display,
        headlineMedium = display.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = display.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = title,
        titleMedium = title.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        titleSmall = title.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = body,
        bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = body.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = label,
        labelMedium = label.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelSmall = label.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    )
}

object QuotaRabbitFontFamilies {
    private val weights = arrayOf(
        FontWeight.W400,
        FontWeight.W500,
        FontWeight.W600,
        FontWeight.W700,
        FontWeight.W800,
        FontWeight(850),
    )

    val system = FontFamily.Default
    val geistSans = bundledFontFamily(R.font.geist_variable)
    val geistMono = bundledFontFamily(R.font.geist_mono_variable)
    val inter = bundledFontFamily(R.font.inter_variable)
    val jetBrainsMono = bundledFontFamily(R.font.jetbrains_mono_variable)

    private fun bundledFontFamily(resourceId: Int): FontFamily =
        FontFamily(*weights.map { weight -> Font(resourceId, weight = weight) }.toTypedArray())
}

private val SystemQuotaRabbitTextStyles = buildQuotaRabbitTextStyles(
    uiFamily = QuotaRabbitFontFamilies.system,
    numberFamily = QuotaRabbitFontFamilies.system,
)

private val LocalQuotaRabbitTextStyles = staticCompositionLocalOf { SystemQuotaRabbitTextStyles }

object QuotaRabbitTypography {
    val display: TextStyle = SystemQuotaRabbitTextStyles.display
    val title: TextStyle = SystemQuotaRabbitTextStyles.title
    val body: TextStyle = SystemQuotaRabbitTextStyles.body
    val label: TextStyle = SystemQuotaRabbitTextStyles.label
    val number: TextStyle = SystemQuotaRabbitTextStyles.number
    val material: Typography = SystemQuotaRabbitTextStyles.material

    val current: QuotaRabbitTextStyles
        @Composable get() = LocalQuotaRabbitTextStyles.current

    fun forScheme(scheme: QuotaRabbitFontScheme): QuotaRabbitTextStyles =
        when (scheme) {
            QuotaRabbitFontScheme.SystemDefault -> SystemQuotaRabbitTextStyles
            QuotaRabbitFontScheme.GeistHybrid -> buildQuotaRabbitTextStyles(
                uiFamily = QuotaRabbitFontFamilies.geistSans,
                numberFamily = QuotaRabbitFontFamilies.geistMono,
            )
            QuotaRabbitFontScheme.InterJetBrainsMono -> buildQuotaRabbitTextStyles(
                uiFamily = QuotaRabbitFontFamilies.inter,
                numberFamily = QuotaRabbitFontFamilies.jetBrainsMono,
            )
            QuotaRabbitFontScheme.MonoFocusGeistMono -> buildQuotaRabbitTextStyles(
                uiFamily = QuotaRabbitFontFamilies.geistMono,
                numberFamily = QuotaRabbitFontFamilies.geistMono,
            )
        }
}

private fun buildQuotaRabbitTextStyles(
    uiFamily: FontFamily,
    numberFamily: FontFamily,
): QuotaRabbitTextStyles =
    QuotaRabbitTextStyles(
        display = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        ),
        title = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        ),
        body = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        ),
        label = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
        number = TextStyle(
            fontFamily = numberFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = "tnum",
        ),
    )

object QuotaRabbitShapes {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(13.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(22.dp)
    val xl = RoundedCornerShape(28.dp)
    val screen = RoundedCornerShape(39.dp)
    val pill = RoundedCornerShape(999.dp)

    val material = Shapes(
        extraSmall = xs,
        small = sm,
        medium = md,
        large = lg,
        extraLarge = xl,
    )
}

private fun lightScheme(p: QuotaRabbitColorPalette) = lightColorScheme(
    primary = p.accent,
    onPrimary = p.surface,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accent,
    inversePrimary = p.accent,
    secondary = p.secondary,
    onSecondary = p.surface,
    secondaryContainer = p.neutralAlt,
    onSecondaryContainer = p.secondary,
    tertiary = p.tertiary,
    onTertiary = p.surface,
    tertiaryContainer = p.neutralAlt,
    onTertiaryContainer = p.tertiary,
    background = p.neutral,
    onBackground = p.primary,
    surface = p.surface,
    onSurface = p.primary,
    surfaceVariant = p.surfaceSoft,
    onSurfaceVariant = p.secondary,
    surfaceTint = Color.Transparent,
    inverseSurface = p.primary,
    inverseOnSurface = p.surface,
    outline = p.border,
    outlineVariant = p.border,
    scrim = p.primary,
    surfaceBright = p.surface,
    surfaceDim = p.neutralAlt,
    surfaceContainer = p.neutral,
    surfaceContainerHigh = p.surface,
    surfaceContainerHighest = p.neutralAlt,
    surfaceContainerLow = p.surfaceSoft,
    surfaceContainerLowest = p.surface,
    error = p.danger,
    onError = p.surface,
    errorContainer = p.dangerSoft,
    onErrorContainer = p.danger,
    primaryFixed = p.accentSoft,
    primaryFixedDim = p.accentSoft,
    onPrimaryFixed = p.accent,
    onPrimaryFixedVariant = p.accent,
    secondaryFixed = p.neutralAlt,
    secondaryFixedDim = p.neutralAlt,
    onSecondaryFixed = p.primary,
    onSecondaryFixedVariant = p.secondary,
    tertiaryFixed = p.neutralAlt,
    tertiaryFixedDim = p.neutralAlt,
    onTertiaryFixed = p.primary,
    onTertiaryFixedVariant = p.tertiary,
)

private fun darkScheme(p: QuotaRabbitColorPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = Color(0xFF08121F), // dark ink reads on the bright accent
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accent,
    secondary = p.secondary,
    onSecondary = p.primary,
    secondaryContainer = p.neutralAlt,
    onSecondaryContainer = p.primary,
    tertiary = p.tertiary,
    onTertiary = p.primary,
    background = p.neutral,
    onBackground = p.primary,
    surface = p.surface,
    onSurface = p.primary,
    surfaceVariant = p.surfaceSoft,
    onSurfaceVariant = p.secondary,
    surfaceTint = Color.Transparent,
    inverseSurface = p.primary,
    inverseOnSurface = p.neutral,
    outline = p.border,
    outlineVariant = p.border,
    scrim = Color(0xFF000000),
    surfaceBright = p.surface,
    surfaceDim = p.neutral,
    surfaceContainer = p.neutral,
    surfaceContainerHigh = p.surface,
    surfaceContainerHighest = p.neutralAlt,
    surfaceContainerLow = p.surfaceSoft,
    surfaceContainerLowest = p.surface,
    error = p.danger,
    onError = Color(0xFF2B0F0F),
    errorContainer = p.dangerSoft,
    onErrorContainer = p.danger,
)

internal fun materialScheme(palette: QuotaRabbitColorPalette, dark: Boolean) =
    if (dark) darkScheme(palette) else lightScheme(palette)

@Composable
fun QuotaRabbitTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScheme: QuotaRabbitFontScheme = QuotaRabbitFontScheme.SystemDefault,
    content: @Composable () -> Unit,
) {
    val dark = resolveDarkAppearance(themeMode, isSystemInDarkTheme())
    val palette = if (dark) DarkQuotaRabbitColors else LightQuotaRabbitColors
    val typography = QuotaRabbitTypography.forScheme(fontScheme)
    CompositionLocalProvider(LocalQuotaRabbitColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme(palette, dark),
            typography = typography.material,
            shapes = QuotaRabbitShapes.material,
        ) {
            CompositionLocalProvider(
                LocalQuotaRabbitTextStyles provides typography,
                content = content,
            )
        }
    }
}
