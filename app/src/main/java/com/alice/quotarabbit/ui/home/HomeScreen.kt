package com.alice.quotarabbit.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alice.quotarabbit.ui.components.GlassPullToRefreshIndicator
import com.alice.quotarabbit.ui.components.ProviderFilterChips
import com.alice.quotarabbit.ui.components.ProviderFilterOption
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alice.quotarabbit.R
import com.alice.quotarabbit.data.currency.ExchangeRateReader
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceReader
import com.alice.quotarabbit.domain.settings.NotificationPreferenceReader
import com.alice.quotarabbit.ui.motion.rememberQuotaRabbitAnimatorsEnabled
import com.alice.quotarabbit.ui.theme.QuotaRabbitSpacing
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import com.alice.quotarabbit.ui.theme.QuotaRabbitTypography

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    currentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    refreshUseCase: HomeRefreshUseCase,
    trendHistoryLoader: HomeTrendHistoryLoader = HomeTrendHistoryLoader { _, _ -> emptyList() },
    notificationPreferenceReader: NotificationPreferenceReader,
    currencyPreferenceReader: CurrencyPreferenceReader,
    exchangeRateReader: ExchangeRateReader,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            currentQuotaStateLoader = currentQuotaStateLoader,
            refreshUseCase = refreshUseCase,
            trendHistoryLoader = trendHistoryLoader,
            notificationPreferenceReader = notificationPreferenceReader,
            currencyPreferenceReader = currencyPreferenceReader,
            exchangeRateReader = exchangeRateReader,
        ),
    ),
    onLoginClick: () -> Unit = {},
    onManageAccountsClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.loadCurrentState()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadCurrentState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    HomeScreen(
        uiState = uiState,
        modifier = modifier,
        onRefreshClick = viewModel::refreshNow,
        onLoginClick = onLoginClick,
        onManageAccountsClick = onManageAccountsClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
    onRefreshClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onManageAccountsClick: () -> Unit = {},
) {
    // Only saved account providers are offered, and deleted providers revert to All.
    var selectedProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    val providers = uiState.accountCards.distinctBy { it.providerId }
        .map { ProviderFilterOption(it.providerId.value, it.providerDisplayName) }
    val effectiveProviderId = selectedProviderId?.takeIf { id -> providers.any { it.id == id } }
    val visibleCards = uiState.accountCards.filter { effectiveProviderId == null || it.providerId.value == effectiveProviderId }
    // All network work remains behind the existing bounded refresh coordinator.
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefreshClick,
        modifier = modifier.fillMaxSize(),
        state = pullState,
        indicator = {
            GlassPullToRefreshIndicator(
                isRefreshing = uiState.isRefreshing,
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize().testTag("dashboard-list"),
            contentPadding = PaddingValues(
                start = QuotaRabbitSpacing.lg,
                end = QuotaRabbitSpacing.lg,
                top = QuotaRabbitSpacing.xl,
                bottom = QuotaRabbitSpacing.bottomNavigationClearance + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
        ) {
            item(key = "header") {
                HomeHeader(uiState = uiState, onRefreshClick = onRefreshClick)
            }
            if (providers.isNotEmpty()) {
                item(key = "provider-filter") {
                    ProviderFilterChips(providers, effectiveProviderId) { selectedProviderId = it }
                }
            }

            if (uiState.accountCards.isNotEmpty()) {
                // Do not group by provider here: all selected accounts must precede
                // unselected accounts globally, even when they belong to different providers.
                items(
                    items = visibleCards,
                    key = { "account:${it.providerId.value}:${it.localAccountId.value}" },
                ) { card ->
                    HomeDashboardAccountCard(card, onManageAccountsClick)
                }
                // The trend is for the app-wide current account, not every provider's
                // separately pinned account. Never label another provider's trend as this filter.
                uiState.account?.takeIf {
                    effectiveProviderId == null || it.providerId?.value == effectiveProviderId
                }?.let { account ->
                    item(key = "current-trend") {
                        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm)) {
                            Text(
                                text = stringResource(R.string.dashboard_trend_account, account.displayName),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HomeTrendCard(trend = uiState.trend)
                        }
                    }
                }
            } else if (uiState.contentStatus == HomeContentStatus.Unauthenticated) {
                item(key = "empty") {
                    HomeActionCard(
                        uiState = uiState,
                        onLoginClick = onLoginClick,
                    )
                }
            } else if (uiState.account == null) {
                item(key = "loading") {
                    uiState.loading?.let { HomeLoadingCard(loading = it) }
                }
            } else {
                // Compatibility for existing single-state previews and injected loaders.
                item(key = "legacy-account") {
                    HomeHeroGlassLayer(uiState = uiState)
                }
                if (uiState.quotaCards.isNotEmpty()) {
                    item(key = "legacy-quota") {
                        HomeQuotaCards(
                            quotaCards = uiState.quotaCards,
                        )
                    }
                    item(key = "legacy-trend") {
                        HomeTrendCard(trend = uiState.trend)
                    }
                } else {
                    item(key = "legacy-loading") {
                        uiState.loading?.let { loading ->
                            HomeLoadingCard(loading = loading)
                        }
                    }
                }
                item(key = "legacy-refresh") {
                    HomeRefreshCard(
                        uiState = uiState,
                    )
                }
                if (uiState.primaryAction != null || uiState.secondaryAction != null) {
                    item(key = "legacy-action") {
                        HomeActionCard(
                            uiState = uiState,
                            onLoginClick = onLoginClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    uiState: HomeUiState,
    onRefreshClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackedHeader = maxWidth < 360.dp && LocalDensity.current.fontScale > 1.2f
        val titleStyle = if (stackedHeader) MaterialTheme.typography.headlineMedium else QuotaRabbitTypography.current.display
        Column(verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm)) {
            if (stackedHeader) {
                Text(
                    text = stringResource(uiState.titleResId),
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("home-title"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.md),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(QuotaRabbitSpacing.sm),
                ) {
                    if (!stackedHeader) Text(
                        text = stringResource(uiState.titleResId),
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("home-title"),
                    )
                    Text(
                        text = stringResource(
                            if (uiState.accountCards.isNotEmpty()) R.string.dashboard_subtitle
                            else uiState.statusDescriptionResId,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.contentStatus != HomeContentStatus.Unauthenticated || uiState.accountCards.isNotEmpty()) {
                    HomeRefreshIconButton(
                        isRefreshing = uiState.isRefreshing,
                        onRefreshClick = onRefreshClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRefreshIconButton(
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
) {
    val animatorsEnabled = rememberQuotaRabbitAnimatorsEnabled()
    val rotation = if (isRefreshing && animatorsEnabled) {
        val transition = rememberInfiniteTransition(label = "home_refresh_icon")
        val animatedRotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
            ),
            label = "home_refresh_icon_rotation",
        )
        animatedRotation
    } else {
        0f
    }
    IconButton(
        onClick = onRefreshClick,
        enabled = !isRefreshing,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_action_refresh),
            contentDescription = stringResource(R.string.dashboard_refresh_all),
            // Manual refresh is the primary header action; accent stays visible on both light and
            // dark backgrounds (the default content color could fall back to near-invisible in dark).
            tint = QuotaRabbitTheme.colors.accent,
            modifier = Modifier.graphicsLayer {
                rotationZ = if (isRefreshing) rotation else 0f
            },
        )
    }
}
