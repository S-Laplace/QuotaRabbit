package com.alice.quotarabbit.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.settings.LanguagePreference
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme

@Composable
internal fun AppearanceCard(
    appearance: SettingsAppearanceUi,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLanguageClick: () -> Unit,
) {
    SettingsSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            ThemeModeSegmentedControl(
                selected = appearance.themeMode,
                onSelected = onThemeModeSelected,
            )
            Text(
                text = stringResource(R.string.settings_appearance_widget_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(color = QuotaRabbitTheme.colors.border)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(onClick = onLanguageClick)
                    .testTag("settings-app-language"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_language_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_language_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = QuotaRabbitTheme.colors.secondary)
                }
                val languageLabel = when (appearance.language) {
                    LanguagePreference.System -> stringResource(R.string.settings_language_system)
                    LanguagePreference.Korean -> stringResource(R.string.settings_language_korean)
                    LanguagePreference.English -> stringResource(R.string.settings_language_english)
                    LanguagePreference.SimplifiedChinese -> stringResource(R.string.settings_language_chinese)
                }
                Text(languageLabel, style = MaterialTheme.typography.labelMedium,
                    color = QuotaRabbitTheme.colors.accent)
                Text("›", style = MaterialTheme.typography.titleLarge,
                    color = QuotaRabbitTheme.colors.secondary)
            }
        }
    }
}

@Composable
private fun ThemeModeSegmentedControl(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.LIGHT to R.string.settings_appearance_light,
        ThemeMode.DARK to R.string.settings_appearance_dark,
        ThemeMode.SYSTEM to R.string.settings_appearance_system,
    )
    val pillShape = RoundedCornerShape(999.dp)
    val trackColor = QuotaRabbitTheme.colors.neutralAlt
    val selectedColor = QuotaRabbitTheme.colors.accent
    // Dark ink reads better on the brighter dark-mode accent; white on the darker light-mode accent.
    val onSelectedColor = if (QuotaRabbitTheme.colors.isDark) Color(0xFF08121F) else Color.White
    val onUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val labelMeasurements = options.map { (_, labelResId) ->
        textMeasurer.measure(
            text = stringResource(labelResId),
            style = labelStyle,
            softWrap = false,
            maxLines = 1,
        )
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag("settings-appearance")) {
        val availableWidth = maxWidth
        val trackPaddingPx = with(density) { 4.dp.roundToPx() } * 2
        val segmentPaddingPx = with(density) { 8.dp.roundToPx() } * 2
        val segmentWidthPx = (constraints.maxWidth - trackPaddingPx) / options.size
        val labelsFit = labelMeasurements.all { it.size.width + segmentPaddingPx <= segmentWidthPx }
        val stacked = !labelsFit || density.fontScale >= 1.3f
        val trackShape = if (stacked) QuotaRabbitShapes.lg else pillShape
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(trackColor, trackShape)
                .border(1.dp, QuotaRabbitTheme.colors.border, trackShape)
                .padding(4.dp),
        ) {
            if (stacked) {
                Column(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.forEach { (mode, labelResId) ->
                        val isSelected = selected == mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(pillShape)
                                .background(if (isSelected) selectedColor else Color.Transparent)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onSelected(mode) },
                                )
                                .testTag("settings-appearance:${mode.name.lowercase()}")
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(labelResId),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) onSelectedColor else onUnselectedColor,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                val segmentWidth = (availableWidth - 8.dp) / options.size
                val labelHeight = labelMeasurements.maxOf { it.size.height }
                val optionHeight = maxOf(48.dp, with(density) { labelHeight.toDp() } + 16.dp)
                val indicatorOffset by animateDpAsState(
                    targetValue = segmentWidth * selectedIndex,
                    animationSpec = if (animatorsEnabled) {
                        tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    } else {
                        snap()
                    },
                    label = "appearance_indicator_offset",
                )
                // Single sliding selected pill that animates between segments (like the bottom tab bar).
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(segmentWidth)
                        .height(optionHeight)
                        .background(selectedColor, pillShape),
                )
                Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                    options.forEach { (mode, labelResId) ->
                        val isSelected = selected == mode
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) onSelectedColor else onUnselectedColor,
                            animationSpec = if (animatorsEnabled) tween(durationMillis = 260) else snap(),
                            label = "appearance_text_color",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(optionHeight)
                                .clip(pillShape)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onSelected(mode) },
                                )
                                .testTag("settings-appearance:${mode.name.lowercase()}")
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(labelResId),
                                style = labelStyle,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersistentNotificationCard(
    persistentNotification: SettingsPersistentNotificationUi,
    onEnabledChanged: (Boolean) -> Unit,
    onAccountClick: () -> Unit,
    onWindowClick: () -> Unit,
) {
    SettingsSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            SwitchRow(
                titleResId = R.string.settings_status_notification_title,
                descriptionResId = R.string.settings_status_notification_description,
                checked = persistentNotification.enabled,
                onCheckedChange = onEnabledChanged,
            )
            ChoiceSummaryTextRow(
                titleResId = R.string.settings_notification_account_title,
                descriptionResId = null,
                valueText = persistentNotification.accountSelection.displayLabel(),
                onClick = onAccountClick,
            )
            ChoiceSummaryTextRow(
                titleResId = R.string.settings_notification_display_quota_title,
                descriptionResId = null,
                valueText = persistentNotification.windowChoices
                    .firstOrNull { it.windowId == persistentNotification.selectedWindowId }
                    ?.let { stringResource(it.labelResId) }
                    ?: stringResource(R.string.settings_value_placeholder),
                onClick = onWindowClick,
            )
        }
    }
}

@Composable
internal fun AlertsCard(
    alerts: SettingsAlertsUi,
    targetCurrency: String,
    onAccountErrorsChanged: (Boolean) -> Unit,
    onCautionThresholdChanged: (Int) -> Unit,
    onWarningThresholdChanged: (Int) -> Unit,
    onBalanceCautionChanged: (Double) -> Unit,
    onBalanceWarningChanged: (Double) -> Unit,
    onCurrencyTargetClick: () -> Unit,
) {
    SettingsSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            SwitchRow(
                titleResId = R.string.settings_account_errors_title,
                descriptionResId = R.string.settings_account_errors_description,
                checked = alerts.accountErrorsEnabled,
                onCheckedChange = onAccountErrorsChanged,
            )
            ThresholdSlider(
                titleResId = R.string.settings_threshold_caution,
                value = alerts.thresholds.caution,
                valueRange = 2f..99f,
                color = QuotaRabbitTheme.colors.warning,
                onValueChange = onCautionThresholdChanged,
            )
            ThresholdSlider(
                titleResId = R.string.settings_threshold_warning,
                value = alerts.thresholds.warning,
                valueRange = 1f..98f,
                color = QuotaRabbitTheme.colors.danger,
                onValueChange = onWarningThresholdChanged,
            )
            Text(
                text = stringResource(R.string.settings_threshold_limit_format, alerts.thresholds.limit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Balance-type providers (DeepSeek) alert on a currency amount instead of a percent.
            // Display currency is shown inline so the unit is obvious alongside the thresholds.
            ChoiceSummaryTextRow(
                titleResId = R.string.settings_currency_target_title,
                descriptionResId = R.string.settings_currency_target_description,
                valueText = targetCurrency,
                onClick = onCurrencyTargetClick,
            )
            Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs)) {
                Text(
                    text = stringResource(R.string.settings_balance_alert_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_balance_threshold_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val symbol = com.alice.quotarabbit.ui.quota.currencySymbol(targetCurrency)
            BalanceStepperRow(
                titleResId = R.string.settings_threshold_caution,
                color = QuotaRabbitTheme.colors.warning,
                currencySymbol = symbol,
                value = alerts.balanceCaution,
                onDecrement = { onBalanceCautionChanged(alerts.balanceCaution - 1) },
                onIncrement = { onBalanceCautionChanged(alerts.balanceCaution + 1) },
            )
            BalanceStepperRow(
                titleResId = R.string.settings_threshold_warning,
                color = QuotaRabbitTheme.colors.danger,
                currencySymbol = symbol,
                value = alerts.balanceWarning,
                onDecrement = { onBalanceWarningChanged(alerts.balanceWarning - 1) },
                onIncrement = { onBalanceWarningChanged(alerts.balanceWarning + 1) },
            )
        }
    }
}

@Composable
private fun BalanceStepperRow(
    titleResId: Int,
    color: Color,
    currencySymbol: String,
    value: Double,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val title = stringResource(titleResId)
    val decreaseDescription = stringResource(R.string.settings_balance_decrease_description, title)
    val increaseDescription = stringResource(R.string.settings_balance_increase_description, title)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .testTag("settings-balance-threshold-row:${titleResId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.settings_balance_amount_format, currencySymbol, value.toInt()),
            modifier = Modifier.background(
                QuotaRabbitTheme.colors.neutralAlt, RoundedCornerShape(10.dp),
            ).padding(horizontal = 10.dp, vertical = 8.dp).widthIn(min = 50.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        BalanceStepButton(
            isIncrease = false,
            description = decreaseDescription,
            onClick = onDecrement,
            enabled = value > 0.0,
        )
        BalanceStepButton(
            isIncrease = true,
            description = increaseDescription,
            onClick = onIncrement,
        )
    }
}

@Composable
private fun BalanceStepButton(
    isIncrease: Boolean,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier.size(32.dp)
                .background(QuotaRabbitTheme.colors.surfaceSoft, CircleShape)
                .border(1.dp, QuotaRabbitTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val iconColor = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            Canvas(modifier = Modifier.size(20.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val halfLength = 6.dp.toPx()
                val thickness = 2.dp.toPx()
                drawLine(
                    color = iconColor,
                    start = Offset(center.x - halfLength, center.y),
                    end = Offset(center.x + halfLength, center.y),
                    strokeWidth = thickness,
                    cap = StrokeCap.Round,
                )
                if (isIncrease) {
                    drawLine(
                        color = iconColor,
                        start = Offset(center.x, center.y - halfLength),
                        end = Offset(center.x, center.y + halfLength),
                        strokeWidth = thickness,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
