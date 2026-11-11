package com.alice.quotarabbit.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.components.LiquidGlassSurfaceRole
import com.alice.quotarabbit.ui.components.QmLiquidGlassSurface
import com.alice.quotarabbit.ui.motion.QuotaRabbitMotion
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme

@Composable
internal fun QuotaRabbitBottomBar(
    modifier: Modifier = Modifier,
    tabs: List<QuotaRabbitRoute>,
    currentRoute: String,
    onTabSelected: (QuotaRabbitRoute) -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val horizontalInset = if (density.fontScale >= 1.3f) 4.dp else QuotaRabbitSpacing.xl
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(QuotaRabbitTheme.colors.neutral)
            .navigationBarsPadding()
            .padding(
                top = QuotaRabbitSpacing.xxl,
                start = horizontalInset,
                end = horizontalInset,
                bottom = QuotaRabbitSpacing.md,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val widthFraction = if (maxWidth < 360.dp || density.fontScale >= 1.3f) 1f else 0.86f
        val tabSpacing = QuotaRabbitSpacing.xs
        val tabWidth = (maxWidth * widthFraction - 10.dp - tabSpacing * (tabs.size - 1)) / tabs.size
        val labelHeight = tabs.maxOf { tab ->
            textMeasurer.measure(
                text = stringResource(tab.labelResId),
                style = labelStyle,
                constraints = Constraints(maxWidth = with(density) { tabWidth.roundToPx().coerceAtLeast(1) }),
            ).size.height
        }
        // Labels retain their full text; the pill grows with the actual localized font metrics.
        val itemHeight = maxOf(56.dp, 24.dp + with(density) { labelHeight.toDp() } + 16.dp)
        QmLiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .height(maxOf(68.dp, itemHeight + 10.dp))
                .testTag("bottom-navigation"),
            role = LiquidGlassSurfaceRole.Navigation,
            cornerRadius = 34.dp,
            contentPadding = PaddingValues(5.dp),
        ) {
            val currentIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
            val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                val indicatorGeometry = QuotaRabbitMotion.bottomTabIndicatorGeometry(
                    selectedIndex = currentIndex,
                    tabCount = tabs.size,
                    containerWidth = maxWidth.value,
                    itemSpacing = tabSpacing.value,
                )
                val indicatorOffset by animateDpAsState(
                    targetValue = indicatorGeometry.offset.dp,
                    animationSpec = if (animatorsEnabled) {
                        tween(
                            durationMillis = QuotaRabbitMotion.BottomTabDurationMillis,
                            easing = FastOutSlowInEasing,
                        )
                    } else {
                        snap()
                    },
                    label = "bottom_tab_indicator_offset",
                )
                val indicatorWidth by animateDpAsState(
                    targetValue = indicatorGeometry.width.dp,
                    animationSpec = if (animatorsEnabled) {
                        tween(
                            durationMillis = QuotaRabbitMotion.BottomTabDurationMillis,
                            easing = FastOutSlowInEasing,
                        )
                    } else {
                        snap()
                    },
                    label = "bottom_tab_indicator_width",
                )
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(indicatorWidth)
                        .height(itemHeight)
                        .align(Alignment.CenterStart)
                        .clip(QuotaRabbitShapes.pill)
                        .background(
                            QuotaRabbitTheme.colors.accentSoft,
                        ),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(tabSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        QuotaRabbitBottomBarItem(
                            tab = tab,
                            selected = selected,
                            itemHeight = itemHeight,
                            onClick = {
                                if (!selected) {
                                    onTabSelected(tab)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuotaRabbitBottomBarItem(
    tab: QuotaRabbitRoute,
    selected: Boolean,
    itemHeight: Dp,
    onClick: () -> Unit,
) {
    val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
    val tabInteractionSource = remember { MutableInteractionSource() }
    val tabPressIndication = if (QuotaRabbitMotion.bottomTabPressIndicationEnabled()) {
        LocalIndication.current
    } else {
        null
    }
    val colorAnimationSpec = if (animatorsEnabled) {
        tween<Color>(
            durationMillis = QuotaRabbitMotion.BottomTabDurationMillis,
            easing = FastOutSlowInEasing,
        )
    } else {
        snap()
    }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            QuotaRabbitTheme.colors.accent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = colorAnimationSpec,
        label = "bottom_tab_content_color",
    )
    val iconScale by animateFloatAsState(
        targetValue = QuotaRabbitMotion.bottomTabScale(selected),
        animationSpec = if (animatorsEnabled) {
            tween(
                durationMillis = QuotaRabbitMotion.BottomTabDurationMillis,
                easing = FastOutSlowInEasing,
            )
        } else {
            snap()
        },
        label = "bottom_tab_icon_scale",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .height(itemHeight)
            .clip(QuotaRabbitShapes.pill)
            .selectable(
                selected = selected,
                interactionSource = tabInteractionSource,
                indication = tabPressIndication,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag("nav-tab:${tab.route}")
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(
                if (QuotaRabbitTheme.colors.isDark) {
                    when (tab) {
                        QuotaRabbitRoute.Home -> R.drawable.ic_tab_home_dark
                        QuotaRabbitRoute.Account -> R.drawable.ic_tab_account_dark
                        QuotaRabbitRoute.Settings -> R.drawable.ic_tab_settings_dark
                        else -> tab.iconResId
                    }
                } else {
                    tab.iconResId
                },
            ),
            contentDescription = stringResource(tab.contentDescriptionResId),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            // Figma supplies separate light/dark fills; never override them with Compose tint.
            tint = Color.Unspecified,
        )
        Text(
            text = stringResource(tab.labelResId),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
