package com.alice.quotarabbit.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

// These keys contain only clipped display facts. Legacy keys stay readable for existing widgets.
private val selectedAccountsKey = stringPreferencesKey("widget_config_accounts_v1")
private val accountRowsKey = stringPreferencesKey("widget_account_rows_v1")
private val fieldDetailsKey = stringPreferencesKey("widget_field_details_v1")
private val lastSuccessfulRefreshKey = stringPreferencesKey("widget_last_successful_refresh")
private val widgetJson = Json { ignoreUnknownKeys = true }

internal fun Preferences.readSelectedAccounts(): List<WidgetAccountSelection> =
    this[selectedAccountsKey]?.let { raw ->
        runCatching { widgetJson.decodeFromString<List<WidgetAccountSelection>>(raw) }.getOrNull()
    }.orEmpty().take(WIDGET_MAX_ACCOUNTS)

internal fun MutablePreferences.writeSelectedAccounts(accounts: List<WidgetAccountSelection>) {
    if (accounts.isEmpty()) remove(selectedAccountsKey)
    else this[selectedAccountsKey] = widgetJson.encodeToString(accounts.distinct().take(WIDGET_MAX_ACCOUNTS))
}

internal fun Preferences.readAccountRows(): List<WidgetAccountState> =
    this[accountRowsKey]?.let { raw ->
        runCatching { widgetJson.decodeFromString<List<StoredWidgetAccount>>(raw).map { it.toState() } }.getOrNull()
    }.orEmpty().take(WIDGET_MAX_ACCOUNTS)

internal fun MutablePreferences.writeAccountRows(accounts: List<WidgetAccountState>) {
    if (accounts.isEmpty()) remove(accountRowsKey)
    else this[accountRowsKey] = widgetJson.encodeToString(accounts.take(WIDGET_MAX_ACCOUNTS).map { it.toStored() })
}

internal fun Preferences.readFieldDetails(): List<WidgetField>? =
    this[fieldDetailsKey]?.let { raw ->
        runCatching { widgetJson.decodeFromString<List<StoredWidgetField>>(raw).map { it.toState() } }.getOrNull()
    }?.take(WIDGET_MAX_FIELDS)

internal fun MutablePreferences.writeFieldDetails(fields: List<WidgetField>) {
    this[fieldDetailsKey] = widgetJson.encodeToString(fields.take(WIDGET_MAX_FIELDS).map { it.toStored() })
}

internal fun Preferences.readLastSuccessfulRefresh(): Instant? =
    this[lastSuccessfulRefreshKey]?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun MutablePreferences.writeLastSuccessfulRefresh(instant: Instant?) {
    if (instant == null) remove(lastSuccessfulRefreshKey) else this[lastSuccessfulRefreshKey] = instant.toString()
}

@Serializable
private data class StoredWidgetAccount(
    val providerId: String,
    val localAccountId: String,
    val providerName: String,
    val accountName: String?,
    val status: String,
    val tone: String,
    val fields: List<StoredWidgetField>,
    val lastSuccessfulRefreshAt: String?,
) {
    fun toState() = WidgetAccountState(
        providerId, localAccountId, providerName, accountName,
        runCatching { WidgetQuotaStatus.valueOf(status) }.getOrDefault(WidgetQuotaStatus.NoData),
        runCatching { WidgetQuotaTone.valueOf(tone) }.getOrDefault(WidgetQuotaTone.Neutral),
        fields.take(WIDGET_MAX_FIELDS).map { it.toState() },
        lastSuccessfulRefreshAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    )
}

@Serializable
private data class StoredWidgetField(
    val windowId: String,
    val isBalance: Boolean,
    val percent: Int?,
    val balanceAmount: String?,
    val balanceCurrency: String?,
    val resetAt: String?,
    val tone: String,
    val limitWindowSeconds: Int?,
    val displayKind: String,
    val usedCount: Int?,
    val limitCount: Int?,
    val modelName: String?,
) {
    fun toState() = WidgetField(
        windowId, isBalance, percent, balanceAmount, balanceCurrency,
        resetAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        runCatching { WidgetQuotaTone.valueOf(tone) }.getOrDefault(WidgetQuotaTone.Neutral),
        limitWindowSeconds,
        runCatching { QuotaWindowDisplayKind.valueOf(displayKind) }.getOrDefault(QuotaWindowDisplayKind.Percent),
        usedCount, limitCount, modelName,
    )
}

private fun WidgetField.toStored() = StoredWidgetField(
    windowId, isBalance, percent, balanceAmount, balanceCurrency, resetAt?.toString(),
    tone.name, limitWindowSeconds, displayKind.name, usedCount, limitCount, modelName,
)

private fun WidgetAccountState.toStored() = StoredWidgetAccount(
    providerId, localAccountId, providerName, accountName, status.name, tone.name,
    fields.take(WIDGET_MAX_FIELDS).map { it.toStored() }, lastSuccessfulRefreshAt?.toString(),
)
