package com.alice.quotarabbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.ui.theme.accountAccentColor
import com.alice.quotarabbit.ui.theme.avatarColor
import com.alice.quotarabbit.ui.theme.avatarInitialColor

/** The same initial-and-account-color profile avatar used in Home and Account. */
@Composable
internal fun AccountProfileAvatar(
    initial: String,
    avatarColorKey: String,
    accentColorId: String?,
    modifier: Modifier = Modifier,
    minimumSize: Dp = 36.dp,
) {
    val style = MaterialTheme.typography.titleMedium
    val measured = rememberTextMeasurer().measure(initial, style).size
    val size = with(LocalDensity.current) {
        maxOf(minimumSize, maxOf(measured.width, measured.height).toDp() + 8.dp)
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape)
            .background(accountAccentColor(accentColorId) ?: avatarColor(avatarColorKey)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial, color = avatarInitialColor(), style = style, maxLines = 1)
    }
}
