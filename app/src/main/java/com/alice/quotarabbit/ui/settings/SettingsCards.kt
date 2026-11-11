package com.alice.quotarabbit.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme

@Composable
internal fun RefreshCard(
    refresh: SettingsRefreshUi,
    onBackgroundRefreshChanged: (Boolean) -> Unit,
    onIntervalClick: () -> Unit,
    showBatteryOptimizationHint: Boolean = false,
    onBatteryOptimizationClick: () -> Unit = {},
) {
    SettingsSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            SwitchRow(
                titleResId = R.string.settings_background_refresh_title,
                descriptionResId = R.string.settings_background_refresh_description,
                checked = refresh.backgroundRefreshEnabled,
                onCheckedChange = onBackgroundRefreshChanged,
            )
            // Group the interval control with its "last result" status so the caption hugs the row
            // (tight xs gap) instead of floating a full md gap below it, which read as an empty line.
            Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs)) {
                ChoiceSummaryRow(
                    titleResId = R.string.settings_refresh_interval_title,
                    descriptionResId = null,
                    valueResId = refresh.interval.labelResId,
                    onClick = onIntervalClick,
                )
                Text(
                    text = stringResource(
                        R.string.settings_refresh_last_result_format,
                        stringResource(refresh.lastResultLabelResId),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showBatteryOptimizationHint) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs),
                ) {
                    SettingsItemText(
                        titleResId = R.string.settings_battery_optimization_title,
                        descriptionResId = R.string.settings_battery_optimization_description,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = onBatteryOptimizationClick,
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
                        shape = QuotaRabbitShapes.md,
                    ) {
                        Text(text = stringResource(R.string.settings_battery_optimization_action))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DataCard(
    data: SettingsDataUi,
    onRetentionClick: () -> Unit,
    onClearCurrentHistoryClick: () -> Unit,
    onClearAllHistoryClick: () -> Unit,
) {
    SettingsSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            ChoiceSummaryRow(
                titleResId = R.string.settings_retention_title,
                descriptionResId = R.string.settings_retention_description,
                valueResId = data.retention.labelResId,
                onClick = onRetentionClick,
            )
            DestructiveActionRow(
                titleResId = R.string.settings_data_clear_current_title,
                descriptionResId = R.string.settings_data_clear_current_description,
                onClick = onClearCurrentHistoryClick,
            )
            DestructiveActionRow(
                titleResId = R.string.settings_data_clear_all_title,
                descriptionResId = R.string.settings_data_clear_all_description,
                onClick = onClearAllHistoryClick,
            )
        }
    }
}

@Composable
internal fun ChoiceSummaryRow(
    titleResId: Int,
    descriptionResId: Int?,
    valueResId: Int,
    onClick: () -> Unit,
) {
    ChoiceSummaryRowLayout(titleResId, descriptionResId, stringResource(valueResId), onClick)
}

@Composable
internal fun ChoiceSummaryTextRow(
    titleResId: Int,
    descriptionResId: Int?,
    valueText: String,
    onClick: () -> Unit,
) {
    ChoiceSummaryRowLayout(titleResId, descriptionResId, valueText, onClick)
}

/**
 * Keep the whole choice accessible, including long account names and enlarged labels.
 */
@Composable
private fun ChoiceSummaryRowLayout(
    titleResId: Int,
    descriptionResId: Int?,
    valueText: String,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val title = stringResource(titleResId)
    val titleStyle = MaterialTheme.typography.titleMedium
    val valueStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(QuotaRabbitShapes.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = QuotaRabbitSpacing.xs),
    ) {
        val titleWidth = textMeasurer.measure(title, titleStyle, softWrap = false, maxLines = 1).size.width
        val valueWidth = textMeasurer.measure(valueText, valueStyle, softWrap = false, maxLines = 1).size.width
        val spacingWidth = with(density) { QuotaRabbitSpacing.md.roundToPx() + 12.dp.roundToPx() }
        val stacked = density.fontScale >= 1.3f || titleWidth + valueWidth + spacingWidth > constraints.maxWidth
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs),
        ) {
            if (stacked) {
                SettingsItemText(titleResId, null, modifier = Modifier.fillMaxWidth())
                Text(
                    valueText,
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsItemText(titleResId, null, modifier = Modifier.weight(1f))
                    Text(
                        text = valueText,
                        modifier = Modifier.padding(end = 12.dp),
                        style = valueStyle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (descriptionResId != null) {
                Text(
                    text = stringResource(descriptionResId),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ThresholdSlider(
    titleResId: Int,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    val title = stringResource(titleResId)
    Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm)) {
        Text(
            text = stringResource(R.string.settings_threshold_value_format, title, value),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { contentDescription = title },
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = color,
                inactiveTrackColor = QuotaRabbitTheme.colors.neutralAlt,
            ),
            thumb = {
                AppleThresholdThumb(color)
            },
            track = { sliderState ->
                AppleThresholdTrack(
                    value = sliderState.value,
                    valueRange = valueRange,
                    color = color,
                )
            },
        )
    }
}

@Composable
internal fun SettingsNotificationAccountSelection.displayLabel(): String =
    when (this) {
        SettingsNotificationAccountSelection.FollowCurrent ->
            stringResource(R.string.settings_notification_account_follow_current)
        is SettingsNotificationAccountSelection.Account -> displayName
    }

@Composable
private fun AppleThresholdThumb(color: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .shadow(
                elevation = 3.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.14f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .background(Color.White, CircleShape)
            .border(1.dp, color.copy(alpha = 0.18f), CircleShape),
    )
}

@Composable
private fun AppleThresholdTrack(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    color: Color,
) {
    val trackColor = QuotaRabbitTheme.colors.neutralAlt
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        val trackHeight = 7.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
            .coerceIn(0f, 1f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width * fraction, trackHeight),
            cornerRadius = corner,
        )
    }
}
