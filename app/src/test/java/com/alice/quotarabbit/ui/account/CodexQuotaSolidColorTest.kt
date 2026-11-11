package com.alice.quotarabbit.ui.account

import com.alice.quotarabbit.domain.settings.NotificationPreferences
import com.alice.quotarabbit.ui.theme.LightQuotaRabbitColors
import com.alice.quotarabbit.ui.theme.DarkQuotaRabbitColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CodexQuotaSolidColorTest {
    @Test fun paletteMatchesSeverityOnLightAndDarkThemes() {
        val defaults = NotificationPreferences()
        assertEquals(LightQuotaRabbitColors.success, quotaSolidColor(80, defaults, LightQuotaRabbitColors))
        assertEquals(LightQuotaRabbitColors.warning, quotaSolidColor(20, defaults, LightQuotaRabbitColors))
        assertEquals(LightQuotaRabbitColors.danger, quotaSolidColor(10, defaults, LightQuotaRabbitColors))
        assertEquals(DarkQuotaRabbitColors.success, quotaSolidColor(80, defaults, DarkQuotaRabbitColors))
        assertEquals(DarkQuotaRabbitColors.warning, quotaSolidColor(20, defaults, DarkQuotaRabbitColors))
        assertEquals(DarkQuotaRabbitColors.danger, quotaSolidColor(0, defaults, DarkQuotaRabbitColors))
        assertEquals(DarkQuotaRabbitColors.secondary, quotaSolidColor(null, defaults, DarkQuotaRabbitColors))
    }

    @Test fun userDefinedThresholdsOverrideFixedGradientBreakpoints() {
        val custom = NotificationPreferences(cautionThreshold = 70, warningThreshold = 35)
        assertEquals(LightQuotaRabbitColors.warning, quotaSolidColor(50, custom, LightQuotaRabbitColors))
        assertEquals(LightQuotaRabbitColors.danger, quotaSolidColor(20, custom, LightQuotaRabbitColors))
    }
}
