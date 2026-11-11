package com.alice.quotarabbit.ui.analytics

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ThirtyDayBarShapeTest {
    @Test fun verticalThirtyDayBarsHaveSmallerRoundedTopAndSquareBaseline() {
        assertEquals(
            RoundedCornerShape(
                topStart = 3.dp, topEnd = 3.dp,
                bottomStart = 0.dp, bottomEnd = 0.dp,
            ),
            thirtyDayVerticalBarShape,
        )
    }
}
