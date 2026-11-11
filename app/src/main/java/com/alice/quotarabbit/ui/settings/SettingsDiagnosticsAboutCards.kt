package com.alice.quotarabbit.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.update.QuotaRabbitRepository
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme

@Composable
internal fun DiagnosticsCard(
    diagnostics: SettingsDiagnosticsUi,
    onDiagnosticsToggle: () -> Unit,
    onDiagnosticsCopyClick: () -> Unit,
    onDiagnosticsRecheckClick: () -> Unit,
) {
    SettingsSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            SettingsItemText(
                titleResId = R.string.settings_diagnostics_title,
                descriptionResId = R.string.settings_diagnostics_description,
            )
            TextButton(
                onClick = onDiagnosticsToggle,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = QuotaRabbitShapes.md,
            ) {
                val labelResId = if (diagnostics.isExpanded) {
                    R.string.settings_diagnostics_hide
                } else {
                    R.string.settings_diagnostics_show
                }
                Text(text = stringResource(labelResId))
            }
            if (diagnostics.isExpanded) {
                Text(
                    text = diagnostics.copyModel?.text
                        ?: stringResource(R.string.settings_diagnostics_collapsed_state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val actions = listOf(
                    stringResource(R.string.settings_diagnostics_copy) to onDiagnosticsCopyClick,
                    stringResource(R.string.settings_diagnostics_recheck) to onDiagnosticsRecheckClick,
                )
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                val textMeasurer = rememberTextMeasurer()
                val labelStyle = MaterialTheme.typography.labelLarge
                val buttonPadding = ButtonDefaults.ContentPadding
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val buttonWidth = (maxWidth - QuotaRabbitSpacing.sm) / 2
                    val labelWidth = buttonWidth - buttonPadding.calculateLeftPadding(layoutDirection) -
                        buttonPadding.calculateRightPadding(layoutDirection)
                    val labelsFit = actions.all { (label, _) ->
                        textMeasurer.measure(label, labelStyle, softWrap = false, maxLines = 1).size.width <=
                            with(density) { labelWidth.toPx() }
                    }
                    val stacked = maxWidth < 300.dp || density.fontScale >= 1.3f || !labelsFit
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                        maxItemsInEachRow = if (stacked) 1 else 2,
                    ) {
                        actions.forEach { (label, onClick) ->
                            OutlinedButton(
                                onClick = onClick,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = QuotaRabbitShapes.md,
                                contentPadding = buttonPadding,
                            ) {
                                Text(text = label, style = labelStyle, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AboutCard(
    about: SettingsAboutUi,
    onRepositoryClick: () -> Unit,
) {
    SettingsSurfaceCard(modifier = Modifier.testTag("settings-about")) {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            Text(
                text = stringResource(
                    R.string.settings_about_version_format,
                    about.versionName,
                    about.buildName,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_about_data_source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_about_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val repositoryContentDescription = stringResource(R.string.settings_about_repository_content_description)
            OutlinedButton(
                onClick = onRepositoryClick,
                enabled = QuotaRabbitRepository.isConfigured,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = repositoryContentDescription },
                shape = QuotaRabbitShapes.md,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_brand_github),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(text = stringResource(
                        if (QuotaRabbitRepository.isConfigured) R.string.settings_about_repository_label
                        else R.string.settings_about_repository_unavailable,
                    ))
                }
            }
        }
    }
}

@Composable
internal fun AppUpdateCard(
    update: SettingsUpdateUi,
    onCheckClick: () -> Unit,
    onAvailableUpdateClick: () -> Unit,
    onAutoCheckChanged: (Boolean) -> Unit,
    onNotifyOnUpdateChanged: (Boolean) -> Unit,
) {
    val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
    val rotation = if (update.isChecking && animatorsEnabled) {
        val transition = rememberInfiniteTransition(label = "settings_update_refresh_icon")
        val animatedRotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
            ),
            label = "settings_update_refresh_icon_rotation",
        )
        animatedRotation
    } else {
        0f
    }

    SettingsSurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsItemText(
                    titleResId = R.string.settings_update_title,
                    descriptionResId = if (QuotaRabbitRepository.isConfigured) {
                        R.string.settings_update_description
                    } else R.string.settings_update_repository_unavailable,
                    modifier = Modifier.weight(1f),
                )
                if (QuotaRabbitRepository.isConfigured && update.availableUpdate != null) {
                    Box(
                        modifier = Modifier
                            .size(QuotaRabbitSpacing.sm)
                            .background(QuotaRabbitTheme.colors.danger, CircleShape),
                    )
                }
            }

            SwitchRow(
                titleResId = R.string.settings_update_auto_check_title,
                descriptionResId = R.string.settings_update_auto_check_description,
                checked = update.autoCheckEnabled,
                onCheckedChange = onAutoCheckChanged,
                enabled = QuotaRabbitRepository.isConfigured,
            )
            SwitchRow(
                titleResId = R.string.settings_update_notify_title,
                descriptionResId = R.string.settings_update_notify_description,
                checked = update.notifyOnUpdateEnabled,
                onCheckedChange = onNotifyOnUpdateChanged,
                enabled = update.autoCheckEnabled && QuotaRabbitRepository.isConfigured,
            )

            update.availableUpdate?.takeIf { QuotaRabbitRepository.isConfigured }?.let { available ->
                Button(
                    onClick = onAvailableUpdateClick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = QuotaRabbitShapes.md,
                ) {
                    Text(
                        text = stringResource(
                            R.string.settings_update_download_available,
                            available.versionName,
                        ),
                    )
                }
            }

            OutlinedButton(
                onClick = onCheckClick,
                enabled = QuotaRabbitRepository.isConfigured && !update.isChecking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings-update-check"),
                shape = QuotaRabbitShapes.md,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_refresh),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (update.isChecking) rotation else 0f
                        },
                    )
                    Text(
                        text = stringResource(
                            if (update.isChecking) R.string.settings_update_checking else R.string.settings_update_check,
                        ),
                    )
                }
            }
            update.statusLabelResId?.takeIf { QuotaRabbitRepository.isConfigured }?.let { statusLabelResId ->
                Text(
                    text = stringResource(statusLabelResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
