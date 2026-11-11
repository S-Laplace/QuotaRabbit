package com.alice.quotarabbit.ui.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.account.AccountListUseCase
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.providers.codex.network.*
import com.alice.quotarabbit.ui.theme.QuotaRabbitTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal val barColors=listOf(Color(0xFF3071CF),Color(0xFFF69955),Color(0xFF68D18A),
    Color(0xFFE783B9),Color(0xFF9C86D8),Color(0xFF46BEB8))
internal val featureBarColors = listOf(
    Color(0xFF3071CF), Color(0xFF68D18A), Color(0xFFF69955),
    Color(0xFFE783B9), Color(0xFF9C86D8), Color(0xFF46BEB8),
)

/** 30-day vertical bars: subtly rounded top, square baseline. */
internal val thirtyDayVerticalBarShape = RoundedCornerShape(
    topStart = 3.dp, topEnd = 3.dp,
    bottomStart = 0.dp, bottomEnd = 0.dp,
)
internal fun fmt(v:Long?):String=v?.let{NumberFormat.getIntegerInstance().format(it)}?:"—"
internal fun fmt(v:Double?):String=v?.let{String.format(Locale.getDefault(),"%,.1f",it)}?:"—"

@Composable
internal fun featureHoldRows(daily: Map<String, Double>): List<Pair<String, String>> {
    val total = positiveUsageTotal(daily)
    if (total <= 0.0) return listOf(stringResource(R.string.analytics_no_data) to "—")
    return listOf(stringResource(R.string.analytics_hold_total_credits) to fmt(total)) +
        daily.entries.filter { it.value.isFinite() && it.value > 0.0 }
            .sortedByDescending { it.value }.take(6)
            .map { (name, value) ->
                localizedFeatureLabel(name) to "${fmt(value)} · ${formatFeatureUsageShare(value, total)}"
            }
}

@Composable
internal fun modelHoldRows(daily: Map<String, Double>): List<Pair<String, String>> {
    val total = positiveUsageTotal(daily)
    if (total <= 0.0) return listOf(stringResource(R.string.analytics_no_data) to "—")
    return listOf(stringResource(R.string.analytics_hold_total_credits) to fmt(total)) +
        daily.entries.filter { it.value.isFinite() && it.value > 0.0 }
            .sortedByDescending { it.value }.take(6)
            .map { (name, value) ->
                name to "${fmt(value)} · ${formatFeatureUsageShare(value, total)}"
            }
}

@Composable fun AccountUsageAnalytics(
    accountId: LocalAccountId,
    accountListUseCase: AccountListUseCase,
    accountName: String,
    modifier: Modifier = Modifier,
) {
    val palette=QuotaRabbitTheme.colors
    val context=LocalContext.current.applicationContext
    val repository=remember(context){CodexAnalyticsRepository(context)}
    var accountList by remember(accountId.value) {
        mutableStateOf<com.alice.quotarabbit.domain.account.AccountListResult?>(null)
    }
    var accountsLoading by remember(accountId.value) { mutableStateOf(true) }
    var period by remember(accountId.value) { mutableIntStateOf(7) }
    var mode by remember(accountId.value) { mutableStateOf("feature") }
    var retry by remember(accountId.value) { mutableIntStateOf(0) }
    var result by remember(accountId.value, period, retry) { mutableStateOf<AnalyticsResult?>(null) }
    LaunchedEffect(accountId.value, retry) {
        accountsLoading = true
        accountList = null
        accountList = runCatching { accountListUseCase.loadAccounts() }.getOrNull()
        accountsLoading = false
    }
    val account = selectCodexAnalyticsAccount(accountList?.accounts.orEmpty(), accountId)
    LaunchedEffect(accountId.value, account, period, retry) {
        if (account != null) result = repository.fetch(account, period)
    }
    val data=(result as? AnalyticsResult.Loaded)?.report
    Column(
        modifier.fillMaxWidth().testTag("usage-analytics:${accountId.value}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.analytics_usage_analytics),style=MaterialTheme.typography.titleLarge,
                fontWeight=FontWeight.Bold,color=palette.primary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick={retry++}){Text(stringResource(R.string.analytics_refresh))}
        }
        Text(
            accountName,
            color = palette.secondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("analytics-account-scope"),
        )
        Text(stringResource(R.string.analytics_work_agent_activity),
            color=palette.secondary,style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp),
            verticalAlignment=Alignment.CenterVertically){
            for(days in listOf(7,30)) FilterChip(selected=period==days,
                onClick={period=days},label={Text(stringResource(R.string.analytics_period_days, days))},
                modifier=Modifier.testTag("analytics-period-$days"))
        }
        val analyticsModes = listOf(
            "feature" to stringResource(R.string.analytics_by_feature),
            "models" to stringResource(R.string.analytics_by_model),
            "turns" to stringResource(R.string.analytics_by_turn_start),
            "messages" to stringResource(R.string.analytics_messages),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val modesPerRow = if (maxWidth < 350.dp || LocalDensity.current.fontScale >= 1.3f) 2 else 4
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                analyticsModes.chunked(modesPerRow).forEach { rowModes ->
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        rowModes.forEachIndexed { index, (key, label) ->
                            SegmentedButton(
                                selected = mode == key,
                                onClick = { mode = key },
                                shape = SegmentedButtonDefaults.itemShape(index, rowModes.size),
                                icon = {},
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .testTag("analytics-mode-$key"),
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        if (accountsLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("analytics-account-loading"))
        } else if (account == null) {
            AnalyticsPanel(stringResource(R.string.analytics_could_not_load_analytics)) {
                Text(stringResource(R.string.analytics_existing_quota_status_is_unaffected),
                    color=palette.secondary)
            }
        } else {
            if(result==null) LinearProgressIndicator(Modifier.fillMaxWidth())
            (result as? AnalyticsResult.Error)?.let{
                AnalyticsPanel(stringResource(R.string.analytics_could_not_load_analytics)){
                    Text(localizedAnalyticsError(it.message),color=palette.secondary)
                    Text(stringResource(R.string.analytics_existing_quota_status_is_unaffected),color=palette.secondary)
                }
            }
            if(data!=null) {
                val today = LocalDate.now(ZoneId.systemDefault())
                val lastDay = analyticsWindowEnd(data, mode, today)
                val dates = analyticsWindowDates(lastDay, period)
                val windowDays = data.days.filter { day -> dates.any { it.toString() == day.date } }
                if(data.partial) Text(stringResource(R.string.analytics_some_analytics_data_is_unavailable_only_verif),
                    color=palette.secondary,style=MaterialTheme.typography.bodySmall)
                if (mode == "feature") {
                    AnalyticsPanel(stringResource(R.string.analytics_usage_by_feature)) {
                        if (period == 7) {
                            Text(
                                formatSevenDayFeatureRange(lastDay),
                                color = palette.primary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.testTag("analytics-feature-date-range"),
                            )
                            FeatureUsageSevenDayChart(data, lastDay)
                        } else {
                            Text(formatThirtyDayAnalyticsRange(lastDay),
                                color = palette.primary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.testTag("analytics-feature-date-range"))
                            Text(stringResource(R.string.analytics_relative_api_usage),
                                color=palette.secondary,style=MaterialTheme.typography.bodySmall)
                            if (windowFeatures(data, dates).isEmpty()) {
                                Text(stringResource(R.string.analytics_no_feature_analytics_for_the_selected_period),
                                    color=palette.secondary)
                            } else {
                                FeatureUsageThirtyDayChart(data, lastDay)
                            }
                        }
                    }
                } else if (mode == "models") {
                    AnalyticsPanel(stringResource(R.string.analytics_usage_daily_trend)) {
                        Text(
                            if (period == 30) formatThirtyDayAnalyticsRange(lastDay)
                            else formatSevenDayFeatureRange(lastDay),
                            color = palette.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.testTag("analytics-model-date-range"))
                        if (period == 7) ModelUsageSevenDayChart(data, lastDay)
                        else ModelUsagePeriodChart(data, lastDay, period)
                    }
                } else {
                AnalyticsPanel(if(mode=="messages")stringResource(R.string.analytics_messages_daily_trend) else stringResource(R.string.analytics_usage_daily_trend)){
                    Text(
                        if (period == 30) formatThirtyDayAnalyticsRange(lastDay)
                        else formatSevenDayFeatureRange(lastDay),
                        color = palette.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("analytics-$mode-date-range"),
                    )
                    val values = turnMessagePlotValues(data, dates)
                    val max=values.maxOrNull()?.takeIf{it>0.0}?:1.0
                    if (windowDays.isEmpty() || values.none { it > 0.0 })
                        Text(stringResource(R.string.analytics_no_data_for_the_selected_period),
                            color=palette.secondary)
                    else {
                        val heldDate = remember(accountId.value, mode, period, dates) {
                            mutableStateOf<LocalDate?>(null)
                        }
                        Box(Modifier.fillMaxWidth().analyticsPressHoldPlot(
                            dates, heldDate, AnalyticsHoldPlotAxis.HORIZONTAL_FIT)) {
                        Row(Modifier.fillMaxWidth().height(116.dp),
                            horizontalArrangement=Arrangement.spacedBy(3.dp),
                            verticalAlignment=Alignment.Bottom){
                            values.forEachIndexed{i,value->
                                Column(
                                    Modifier.weight(1f)
                                        .testTag("analytics-$mode-day:${dates[i]}"),
                                    verticalArrangement=Arrangement.Bottom,
                                    horizontalAlignment=Alignment.CenterHorizontally,
                                ) {
                                    if (value > 0.0) Box(Modifier.width(if (period == 30) 6.dp else 22.dp)
                                        .height(((value/max)*104).toFloat().coerceIn(2f,104f).dp)
                                        .background(barColors[0],
                                            if (period == 30) thirtyDayVerticalBarShape
                                            else RoundedCornerShape(topStart=4.dp,topEnd=4.dp)))
                                }
                            }
                        }
                        heldDate.value?.let { day ->
                            val dayValue = values.getOrNull(dates.indexOf(day))?.toLong()
                            AnalyticsHoldDetail(day,
                                listOf(stringResource(R.string.analytics_hold_total_turns) to fmt(dayValue)),
                                Modifier.align(Alignment.TopCenter))
                        }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(dates.first().toString(),color=palette.secondary,
                                style=MaterialTheme.typography.labelSmall)
                            Text(dates.last().toString(),color=palette.secondary,
                                style=MaterialTheme.typography.labelSmall)
                        }
                        Text(when(mode) {
                            "turns","messages","models" -> stringResource(R.string.analytics_measured_in_turns)
                            else -> stringResource(R.string.analytics_measured_in_credits)
                        },
                            color=palette.secondary,style=MaterialTheme.typography.labelSmall)
                        // Turn-start and message modes use provider-reported daily turns.
                    }
                }
                }
                when(mode){
                    "feature" -> Unit
                    "models" -> AnalyticsPanel(stringResource(R.string.analytics_usage_by_model)) {
                        Text(stringResource(R.string.analytics_model_credits_and_turns_per_model_token_total),
                            style=MaterialTheme.typography.bodySmall,color=palette.secondary)
                        val modelCredits = dates.flatMap { date ->
                            modelCreditsOnDate(data, date).entries
                        }.groupBy({ it.key }, { it.value })
                            .mapValues { (_, credits) -> credits.sum() }
                        val totalCredits = modelCredits.values.sum()
                        val visibleModels = modelCredits.entries.filter { it.value > 0.0 }
                            .sortedByDescending { it.value }.take(15)
                        for ((name, credits) in visibleModels) {
                            val share = formatFeatureUsageShare(credits, totalCredits)
                            val turns = windowDays.mapNotNull { it.modelTurns[name] }
                                .takeIf { it.isNotEmpty() }?.sum()
                            MetricRow(name, share+" · "+fmt(credits)+" · "+
                                fmt(turns)+stringResource(R.string.analytics_turns))
                        }
                        if(visibleModels.isEmpty())Text(stringResource(R.string.analytics_no_model_data_available),
                            color=palette.secondary)
                    }
                    "turns" -> AnalyticsPanel(stringResource(R.string.analytics_turns_plan_limit_usage)) {
                        Text(stringResource(R.string.analytics_exact_turn_start_times_and_historical_five_ho),
                            color=palette.secondary)
                        MetricRow(stringResource(R.string.analytics_total_turns_in_period),
                            fmt(windowDays.mapNotNull{it.turns}.takeIf{it.isNotEmpty()}?.sum()))
                    }
                    else -> AnalyticsPanel(stringResource(R.string.analytics_daily_turn_records)) {
                        Text(stringResource(R.string.analytics_this_is_the_api_turns_metric_which_may_differ),
                            color=palette.secondary)
                        for(d in windowDays.takeLast(10)) MetricRow(d.date,fmt(d.turns))
                    }
                }
                AnalyticsPanel(stringResource(R.string.analytics_daily_token_usage)){
                    MetricRow(stringResource(R.string.analytics_input_tokens),fmt(windowDays.mapNotNull{it.input}
                        .takeIf{it.isNotEmpty()}?.sum()))
                    MetricRow(stringResource(R.string.analytics_cached_input_tokens),fmt(windowDays.mapNotNull{it.cache}
                        .takeIf{it.isNotEmpty()}?.sum()))
                    MetricRow(stringResource(R.string.analytics_output_tokens),fmt(windowDays.mapNotNull{it.output}
                        .takeIf{it.isNotEmpty()}?.sum()))
                }
                AnalyticsPanel(stringResource(R.string.analytics_tokens_by_client)){
                    for(c in data.clients.take(10)){
                        Text(c.client,color=palette.primary,fontWeight=FontWeight.Medium)
                        MetricRow(stringResource(R.string.analytics_input_tokens), fmt(c.input))
                        MetricRow(stringResource(R.string.analytics_cached_input_tokens), fmt(c.cache))
                        MetricRow(stringResource(R.string.analytics_output_tokens), fmt(c.output))
                    }
                    if(data.clients.isEmpty())Text(stringResource(R.string.analytics_no_data),color=palette.secondary)
                }
            }
            AnalyticsPanel(stringResource(R.string.analytics_plan_usage)){
                val windows=accountList?.latestQuotaSnapshots?.get(account.localAccountId)
                    ?.windows.orEmpty()
                val short=windows.firstOrNull{it.limitWindowSeconds==18000}
                    ?:windows.firstOrNull{it.isPrimaryCandidate}
                val weekly=windows.firstOrNull{it.limitWindowSeconds==604800}
                    ?:windows.firstOrNull{it!=short&&it.usedPercent!=null}
                MetricRow(stringResource(R.string.analytics_5_hour_quota),short?.usedPercent?.let{it.toString()+"%"}?:"—")
                MetricRow(stringResource(R.string.analytics_weekly_quota),weekly?.usedPercent?.let{it.toString()+"%"}?:"—")
                Text(stringResource(R.string.analytics_shows_the_latest_quota_snapshot_only_historic),
                    color=palette.secondary,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun localizedAnalyticsError(message: String): String = when {
    message == "지원하지 않는 요청입니다." -> stringResource(R.string.analytics_error_invalid_request)
    message == "Codex 로그인이 필요합니다." -> stringResource(R.string.analytics_error_login_required)
    message == "계정 인증을 확인할 수 없습니다." -> stringResource(R.string.analytics_error_auth)
    message == "토큰이 만료되었습니다. 계정을 새로고침하세요." ->
        stringResource(R.string.analytics_error_expired)
    message.startsWith("인증 또는 할당량 조회 실패 (") -> stringResource(
        R.string.analytics_error_quota_request,
        message.substringAfterLast("(").substringBefore(")"),
    )
    message.startsWith("일별 사용량 조회 실패 (") -> stringResource(
        R.string.analytics_error_daily_request,
        message.substringAfterLast("(").substringBefore(")"),
    )
    message == "네트워크 또는 분석 API를 확인할 수 없습니다." ->
        stringResource(R.string.analytics_error_network)
    else -> message
}

@Composable private fun AnalyticsPanel(title:String,content:@Composable ColumnScope.()->Unit){
    val c=QuotaRabbitTheme.colors
    Card(Modifier.fillMaxWidth(),border=BorderStroke(1.dp,c.border),
        shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=c.surface)){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(title,color=c.primary,style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.SemiBold)
            content()
        }
    }
}
@Composable private fun MetricRow(label:String,value:String){
    val c=QuotaRabbitTheme.colors
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
        verticalAlignment=Alignment.CenterVertically){
        Text(label,color=c.secondary,style=MaterialTheme.typography.bodySmall,
            modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(value,color=c.primary,fontWeight=FontWeight.Medium,
            style=MaterialTheme.typography.bodySmall)
    }
}
