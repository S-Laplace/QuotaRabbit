package com.alice.quotarabbit.ui.home

import com.alice.quotarabbit.domain.account.ACCOUNT_ACCENT_COLORS
import com.alice.quotarabbit.ui.theme.accountAccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActivityIntensityColorsTest {
    @Test fun `every configured account accent supplies all four levels`() {
        val ids = ACCOUNT_ACCENT_COLORS
        ids.forEach { id ->
            val accent = requireNotNull(accountAccentColor(id))
            val levels = activityIntensityColors(accent)
            assertEquals(4, levels.size)
            assertEquals(accent, levels.last())
            levels.forEachIndexed { index, level ->
                assertEquals(accent, level.copy(alpha = 1f))
                assertEquals(listOf(0.28f, 0.48f, 0.73f, 1f)[index], level.alpha, 0.002f)
            }
        }
        assertNotEquals(
            activityIntensityColors(requireNotNull(accountAccentColor("teal"))),
            activityIntensityColors(requireNotNull(accountAccentColor("rose"))),
        )
    }
}
