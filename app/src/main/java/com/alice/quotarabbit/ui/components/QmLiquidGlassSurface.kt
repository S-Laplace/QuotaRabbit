package com.alice.quotarabbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme

enum class LiquidGlassSurfaceRole {
    Hero,
    Card,
    Navigation,
}

/** Opaque theme surfaces replace the former refraction layers at existing card/navigation sites. */
@Composable
fun QmLiquidGlassSurface(
    modifier: Modifier = Modifier,
    role: LiquidGlassSurfaceRole,
    cornerRadius: Dp,
    contentPadding: PaddingValues,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = if (role == LiquidGlassSurfaceRole.Navigation) {
        QuotaRabbitShapes.pill
    } else {
        RoundedCornerShape(cornerRadius)
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = QuotaRabbitTheme.colors.surface,
        contentColor = QuotaRabbitTheme.colors.primary,
        border = BorderStroke(1.dp, QuotaRabbitTheme.colors.border),
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun QuotaRabbitBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(QuotaRabbitTheme.colors.neutral))
}
