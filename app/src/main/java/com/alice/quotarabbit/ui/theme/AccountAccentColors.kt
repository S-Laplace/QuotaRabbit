package com.alice.quotarabbit.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Optional UI preference; does not change quota health tones or the account's identity key. */
fun accountAccentColor(id: String?): Color? = when (id) {
    "teal" -> Color(0xFF008578)
    "violet" -> Color(0xFF795BC2)
    "amber" -> Color(0xFFAF741C)
    "rose" -> Color(0xFFC05079)
    "slate" -> Color(0xFF64748B)
    "green" -> Color(0xFF3B913F)
    "red" -> Color(0xFFBC4334)
    "brown" -> Color(0xFF765338)
    else -> null
}

fun accountAccentGradient(id: String?, default: Color): Brush {
    val selected = accountAccentColor(id) ?: default
    return Brush.horizontalGradient(
        listOf(selected.copy(alpha = 0.72f), selected),
    )
}
