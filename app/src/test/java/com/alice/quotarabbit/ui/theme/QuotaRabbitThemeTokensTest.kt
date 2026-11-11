package com.alice.quotarabbit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaRabbitThemeTokensTest {
    @Test
    fun `small text retains accessible contrast on its theme surfaces`() {
        listOf(LightQuotaRabbitColors, DarkQuotaRabbitColors).forEach { palette ->
            val textPairs = listOf(
                "primary" to (palette.primary to palette.surface),
                "secondary" to (palette.secondary to palette.surface),
                "primary background" to (palette.primary to palette.neutral),
                "secondary background" to (palette.secondary to palette.neutral),
                "secondary inset surface" to (palette.secondary to palette.surfaceSoft),
                "secondary navigation" to (palette.secondary to palette.neutralAlt),
                "accent navigation" to (palette.accent to palette.neutralAlt),
                "success badge" to (palette.success to palette.successSoft),
                "warning badge" to (palette.warning to palette.warningSoft),
                "danger badge" to (palette.danger to palette.dangerSoft),
            )
            textPairs.forEach { (role, colors) ->
                val foreground = colors.first.luminance()
                val background = colors.second.luminance()
                val ratio = (maxOf(foreground, background) + 0.05f) /
                    (minOf(foreground, background) + 0.05f)
                assertTrue("$role contrast must be at least 4.5:1, was $ratio (dark=" + palette.isDark + ")", ratio >= 4.5f)
            }
        }
    }

    @Test
    fun `solid app color tokens match design values`() {
        assertEquals(Color(0xFF17181C), LightQuotaRabbitColors.primary)
        assertEquals(Color(0xFF5C6470), LightQuotaRabbitColors.secondary)
        assertEquals(Color(0xFF8A94A6), LightQuotaRabbitColors.tertiary)
        assertEquals(Color(0xFFF6F6F6), LightQuotaRabbitColors.neutral)
        assertEquals(Color(0xFFEEEEEE), LightQuotaRabbitColors.neutralAlt)
        assertEquals(Color(0xFFFFFFFF), LightQuotaRabbitColors.surface)
        assertEquals(Color(0xFFF0F0F0), LightQuotaRabbitColors.surfaceSoft)
        assertEquals(Color(0xFFDDDDDD), LightQuotaRabbitColors.border)
        assertEquals(Color(0xFF181818), DarkQuotaRabbitColors.neutral)
        assertEquals(Color(0xFF202020), DarkQuotaRabbitColors.neutralAlt)
        assertEquals(Color(0xFF242424), DarkQuotaRabbitColors.surface)
        assertEquals(Color(0xFF202020), DarkQuotaRabbitColors.surfaceSoft)
        assertEquals(Color(0xFF3A3A3A), DarkQuotaRabbitColors.border)
        assertEquals(Color(0xFF0063C7), LightQuotaRabbitColors.accent)
        assertEquals(Color(0xFFF8FBFF), LightQuotaRabbitColors.accentSoft)
        assertEquals(Color(0xFF147D45), LightQuotaRabbitColors.success)
        assertEquals(Color(0xFFEAF7F0), LightQuotaRabbitColors.successSoft)
        assertEquals(Color(0xFFB45309), LightQuotaRabbitColors.warning)
        assertEquals(Color(0xFFFFF4E4), LightQuotaRabbitColors.warningSoft)
        assertEquals(Color(0xFFB91C1C), LightQuotaRabbitColors.danger)
        assertEquals(Color(0xFFFEEDEE), LightQuotaRabbitColors.dangerSoft)
    }

    @Test
    fun `air glass spacing tokens match design values`() {
        assertEquals(4.dp, QuotaRabbitSpacing.xs)
        assertEquals(8.dp, QuotaRabbitSpacing.sm)
        assertEquals(12.dp, QuotaRabbitSpacing.md)
        assertEquals(16.dp, QuotaRabbitSpacing.lg)
        assertEquals(20.dp, QuotaRabbitSpacing.xl)
        assertEquals(24.dp, QuotaRabbitSpacing.xxl)
    }

    @Test
    fun `air glass shape tokens match design values`() {
        assertEquals(RoundedCornerShape(8.dp), QuotaRabbitShapes.xs)
        assertEquals(RoundedCornerShape(13.dp), QuotaRabbitShapes.sm)
        assertEquals(RoundedCornerShape(16.dp), QuotaRabbitShapes.md)
        assertEquals(RoundedCornerShape(22.dp), QuotaRabbitShapes.lg)
        assertEquals(RoundedCornerShape(28.dp), QuotaRabbitShapes.xl)
        assertEquals(RoundedCornerShape(39.dp), QuotaRabbitShapes.screen)
        assertEquals(RoundedCornerShape(999.dp), QuotaRabbitShapes.pill)
    }

    @Test
    fun `system typography uses readable sans serif roles and tabular numbers`() {
        val styles = QuotaRabbitTypography.forScheme(QuotaRabbitFontScheme.SystemDefault)
        listOf(styles.display, styles.title, styles.body, styles.label, styles.number).forEach {
            assertEquals(FontFamily.Default, it.fontFamily)
        }
        assertEquals(28.sp, styles.display.fontSize)
        assertEquals(36.sp, styles.display.lineHeight)
        assertEquals(FontWeight.Bold, styles.display.fontWeight)
        assertEquals(18.sp, styles.title.fontSize)
        assertEquals(FontWeight.SemiBold, styles.title.fontWeight)
        assertEquals(15.sp, styles.body.fontSize)
        assertEquals(FontWeight.Normal, styles.body.fontWeight)
        assertEquals(13.sp, styles.label.fontSize)
        assertEquals(FontWeight.Medium, styles.label.fontWeight)
        assertEquals(36.sp, styles.number.fontSize)
        assertEquals(44.sp, styles.number.lineHeight)
        assertEquals("tnum", styles.number.fontFeatureSettings)
    }

    @Test
    fun `air glass typography keeps implementation-safe letter spacing`() {
        // Neutral spacing keeps Latin and CJK fallback text aligned without condensed tracking.
        assertEquals(0.sp, QuotaRabbitTypography.display.letterSpacing)
        assertEquals(0.sp, QuotaRabbitTypography.title.letterSpacing)
        assertEquals(0.sp, QuotaRabbitTypography.body.letterSpacing)
        assertEquals(0.sp, QuotaRabbitTypography.label.letterSpacing)
        assertEquals(0.sp, QuotaRabbitTypography.number.letterSpacing)
    }

    @Test
    fun `material typography separates headings body and supporting text`() {
        val material = QuotaRabbitTypography.material

        assertEquals(QuotaRabbitTypography.display, material.headlineLarge)
        assertEquals(22.sp, material.headlineMedium.fontSize)
        assertEquals(28.sp, material.headlineMedium.lineHeight)
        assertEquals(15.sp, material.titleMedium.fontSize)
        assertEquals(20.sp, material.titleMedium.lineHeight)
        assertEquals(15.sp, material.bodyLarge.fontSize)
        assertEquals(14.sp, material.bodyMedium.fontSize)
        assertEquals(12.sp, material.bodySmall.fontSize)
        assertEquals(18.sp, material.bodySmall.lineHeight)
        assertEquals(13.sp, material.labelLarge.fontSize)
        assertEquals(12.sp, material.labelMedium.fontSize)
        assertEquals(12.sp, material.labelSmall.fontSize)
        assertNotEquals(material.bodyLarge, material.bodySmall)
        assertNotEquals(material.titleLarge, material.titleSmall)
        assertNotEquals(material.labelLarge, material.labelSmall)
        listOf(material.headlineLarge, material.titleMedium, material.bodyMedium, material.labelSmall).forEach {
            assertEquals(FontFamily.Default, it.fontFamily)
        }
    }

    @Test
    fun `font schemes map display body label and number families`() {
        val system = QuotaRabbitTypography.forScheme(QuotaRabbitFontScheme.SystemDefault)
        val geist = QuotaRabbitTypography.forScheme(QuotaRabbitFontScheme.GeistHybrid)
        val inter = QuotaRabbitTypography.forScheme(QuotaRabbitFontScheme.InterJetBrainsMono)
        val monoFocus = QuotaRabbitTypography.forScheme(QuotaRabbitFontScheme.MonoFocusGeistMono)

        assertEquals(FontFamily.Default, system.display.fontFamily)
        assertNotEquals(FontFamily.Default, geist.display.fontFamily)
        assertNotEquals(geist.display.fontFamily, geist.number.fontFamily)
        assertNotEquals(inter.display.fontFamily, inter.number.fontFamily)
        assertEquals(monoFocus.display.fontFamily, monoFocus.body.fontFamily)
        assertEquals(monoFocus.display.fontFamily, monoFocus.number.fontFamily)
        assertEquals("tnum", geist.number.fontFeatureSettings)
    }

    @Test
    fun `material color roles map to quotarabbit tokens`() {
        val material = materialScheme(LightQuotaRabbitColors, dark = false)

        assertEquals(LightQuotaRabbitColors.accentSoft, material.primaryContainer)
        assertEquals(LightQuotaRabbitColors.border, material.outlineVariant)
        assertEquals(Color.Transparent, material.surfaceTint)
        assertEquals(LightQuotaRabbitColors.surfaceSoft, material.surfaceContainerLow)
        assertEquals(LightQuotaRabbitColors.neutral, material.surfaceContainer)
        assertEquals(LightQuotaRabbitColors.neutralAlt, material.surfaceContainerHighest)
    }
}
