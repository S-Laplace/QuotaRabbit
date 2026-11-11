package com.alice.quotarabbit.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.providers.ProviderAuthKind
import com.alice.quotarabbit.providers.ProviderConfig
import com.alice.quotarabbit.providers.ProviderRegistry
import com.alice.quotarabbit.ui.components.QuotaRabbitBackdrop
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitShapes
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTypography

/**
 * Lists every registered provider and lets the user pick one to add an account. On selection the
 * caller branches on the provider's auth type. Styled per DESIGN.md (solid theme backdrop, restrained
 * surface cards, brand-icon tiles) and fully resource-backed for zh-rCN + en.
 */
@Composable
fun ProviderSelectionScreen(
    onProviderSelected: (ProviderId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val providers = ProviderRegistry.all

    Box(modifier = modifier.fillMaxSize()) {
        QuotaRabbitBackdrop(modifier = Modifier.fillMaxSize())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = QuotaRabbitSpacing.xl,
                end = QuotaRabbitSpacing.xl,
                top = QuotaRabbitSpacing.xxl,
                bottom = QuotaRabbitSpacing.bottomNavigationClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
        ) {
            item {
                ProviderSelectionHeader(onBack = onBack)
                Spacer(modifier = Modifier.height(QuotaRabbitSpacing.sm))
            }
            items(providers, key = { it.providerId.value }) { provider ->
                ProviderListItem(
                    provider = provider,
                    onClick = { onProviderSelected(provider.providerId) },
                )
            }
        }
    }
}

@Composable
private fun ProviderSelectionHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.xs),
        ) {
            Text(
                text = stringResource(R.string.add_provider_title),
                style = QuotaRabbitTypography.current.display,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.add_provider_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onBack) {
            Text(text = stringResource(R.string.auth_back))
        }
    }
}

@Composable
internal fun ProviderListItem(
    provider: ProviderConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("provider-card:${provider.providerId.value}")
            .clip(QuotaRabbitShapes.lg)
            .background(QuotaRabbitTheme.colors.surfaceSoft)
            .clickable(onClick = onClick)
            .padding(QuotaRabbitSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(QuotaRabbitTheme.colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(provider.iconResId),
                    contentDescription = null,
                    tint = QuotaRabbitTheme.colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (largeText) {
                Spacer(Modifier.weight(1f))
            } else {
                ProviderIdentity(provider, Modifier.weight(1f))
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = QuotaRabbitTheme.colors.tertiary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(-90f),
            )
        }
        if (largeText) ProviderIdentity(provider, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProviderIdentity(provider: ProviderConfig, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = provider.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = authTypeLabel(provider.authKind),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun authTypeLabel(authKind: ProviderAuthKind): String = stringResource(
    when (authKind) {
        ProviderAuthKind.OAuthWebView -> R.string.auth_method_oauth
        ProviderAuthKind.ApiKeyImport -> R.string.auth_method_api_key
        ProviderAuthKind.CookieAuth -> R.string.auth_method_cookie
        ProviderAuthKind.OAuthPkceLogin -> R.string.auth_method_oauth_pkce
        ProviderAuthKind.DeviceCodeLogin -> R.string.auth_method_device_code
    },
)
