package com.alice.quotarabbit.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.providers.ProviderRegistry

/** Options must come from saved accounts, not the full provider registry. */
data class ProviderFilterOption(val id: String, val displayName: String)

@Composable
fun ProviderFilterChips(
    providers: List<ProviderFilterOption>,
    selectedProviderId: String?,
    onProviderSelected: (String?) -> Unit,
) {
    if (providers.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("provider-filter-row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderChoiceChip(
            label = stringResource(R.string.provider_filter_all),
            selected = selectedProviderId == null,
            tag = "provider-filter:all",
            providerIconResId = null,
            onClick = { onProviderSelected(null) },
        )
        providers.forEach { provider ->
            ProviderChoiceChip(
                label = provider.displayName,
                selected = selectedProviderId == provider.id,
                tag = "provider-filter:${provider.id}",
                providerIconResId = ProviderRegistry.iconFor(ProviderId(provider.id)),
                onClick = { onProviderSelected(provider.id) },
            )
        }
    }
}

@Composable
private fun ProviderChoiceChip(
    label: String,
    selected: Boolean,
    tag: String,
    providerIconResId: Int?,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                providerIconResId?.let { iconResId ->
                    Icon(
                        painter = painterResource(iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).testTag("$tag-icon"),
                    )
                }
                Text(label)
            }
        },
        shape = CircleShape,
        // Material 3 renders a 32dp chip while retaining its minimum interactive target.
        modifier = Modifier.testTag(tag),
    )
}
