package com.alice.quotarabbit.ui.settings

import androidx.annotation.StringRes
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.currency.CurrencyPreferenceStore
import com.alice.quotarabbit.domain.currency.CurrencyPreferences
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.settings.DEFAULT_BALANCE_CAUTION_THRESHOLD
import com.alice.quotarabbit.domain.settings.DEFAULT_BALANCE_WARNING_THRESHOLD
import com.alice.quotarabbit.domain.settings.DEFAULT_CAUTION_THRESHOLD
import com.alice.quotarabbit.domain.settings.DEFAULT_NOTIFICATION_WINDOW_ID
import com.alice.quotarabbit.domain.settings.DEFAULT_WARNING_THRESHOLD
import com.alice.quotarabbit.domain.settings.LIMIT_THRESHOLD
import com.alice.quotarabbit.domain.settings.LanguagePreference
import com.alice.quotarabbit.domain.theme.AppearancePreferenceStore
import com.alice.quotarabbit.domain.theme.ThemeMode
import com.alice.quotarabbit.domain.update.AppUpdateInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class SettingsNotificationWindowChoice(
    val windowId: QuotaWindowId,
    @get:StringRes val labelResId: Int,
)

enum class SettingsRetentionOption(val days: Int?, @get:StringRes val labelResId: Int) {
    SevenDays(7, R.string.settings_retention_7_days),
    ThirtyDays(30, R.string.settings_retention_30_days),
    NinetyDays(90, R.string.settings_retention_90_days),
    Forever(null, R.string.settings_retention_forever),
}

enum class SettingsPendingDataAction(@get:StringRes val titleResId: Int, @get:StringRes val messageResId: Int) {
    ClearCurrentHistory(
        R.string.settings_data_clear_current_confirm_title,
        R.string.settings_data_clear_current_confirm_message,
    ),
    ClearAllHistory(
        R.string.settings_data_clear_all_confirm_title,
        R.string.settings_data_clear_all_confirm_message,
    ),
}

enum class SettingsRefreshInterval(val minutes: Int, @get:StringRes val labelResId: Int) {
    Fifteen(15, R.string.settings_refresh_interval_15min),
    Thirty(30, R.string.settings_refresh_interval_30min),
    Manual(0, R.string.settings_refresh_interval_manual);

    companion object {
        fun fromMinutes(minutes: Int): SettingsRefreshInterval =
            entries.firstOrNull { it.minutes == minutes } ?: Fifteen
    }
}

enum class SettingsChoiceDialog {
    NotificationAccount,
    NotificationWindow,
    Retention,
    RefreshInterval,
    CurrencyTarget,
    Language,
}

data class SettingsAlertThresholdsUi(
    val caution: Int = DEFAULT_CAUTION_THRESHOLD,
    val warning: Int = DEFAULT_WARNING_THRESHOLD,
    val limit: Int = LIMIT_THRESHOLD,
)

data class SettingsAlertsUi(
    val statusNotificationEnabled: Boolean = true,
    val quotaAlertsEnabled: Boolean = true,
    val accountErrorsEnabled: Boolean = true,
    val thresholds: SettingsAlertThresholdsUi = SettingsAlertThresholdsUi(),
    // Balance-type providers (e.g. DeepSeek) alert on a currency amount, not a percent.
    val balanceCaution: Double = DEFAULT_BALANCE_CAUTION_THRESHOLD,
    val balanceWarning: Double = DEFAULT_BALANCE_WARNING_THRESHOLD,
)

sealed interface SettingsNotificationAccountSelection {
    data object FollowCurrent : SettingsNotificationAccountSelection

    data class Account(
        val providerId: ProviderId,
        val localAccountId: LocalAccountId,
        val displayName: String,
    ) : SettingsNotificationAccountSelection
}

data class SettingsNotificationAccountChoice(
    val selection: SettingsNotificationAccountSelection,
) {
    val stableId: String =
        when (selection) {
            SettingsNotificationAccountSelection.FollowCurrent -> "follow_current"
            is SettingsNotificationAccountSelection.Account -> selection.localAccountId.value
        }
}

data class SettingsPersistentNotificationUi(
    val enabled: Boolean = true,
    val accountSelection: SettingsNotificationAccountSelection = SettingsNotificationAccountSelection.FollowCurrent,
    val accountChoices: List<SettingsNotificationAccountChoice> = listOf(
        SettingsNotificationAccountChoice(SettingsNotificationAccountSelection.FollowCurrent),
    ),
    val selectedWindowId: QuotaWindowId = DEFAULT_NOTIFICATION_WINDOW_ID,
    val windowChoices: List<SettingsNotificationWindowChoice> = emptyList(),
)

data class SettingsRefreshUi(
    val backgroundRefreshEnabled: Boolean = true,
    val interval: SettingsRefreshInterval = SettingsRefreshInterval.Fifteen,
    @get:StringRes val lastResultLabelResId: Int = R.string.settings_refresh_last_result_no_attempts,
)

data class SettingsCurrencyUi(
    val targetCurrency: String = CurrencyPreferences.DEFAULT_TARGET_CURRENCY,
    val supportedCurrencies: List<String> = CurrencyPreferences.SUPPORTED_TARGET_CURRENCIES,
)

data class SettingsAppearanceUi(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: LanguagePreference = LanguagePreference.System,
)

/** Reschedules the background refresh worker when the interval changes (0 minutes = cancel/manual). */
fun interface BackgroundRefreshScheduler {
    fun applyIntervalMinutes(minutes: Int)
}

object NoopBackgroundRefreshScheduler : BackgroundRefreshScheduler {
    override fun applyIntervalMinutes(minutes: Int) = Unit
}

/** Schedules or cancels the daily update-check worker when the auto-check toggle changes. */
fun interface UpdateCheckScheduler {
    fun setAutoCheckEnabled(enabled: Boolean)
}

object NoopUpdateCheckScheduler : UpdateCheckScheduler {
    override fun setAutoCheckEnabled(enabled: Boolean) = Unit
}

internal object NoopCurrencyPreferenceStore : CurrencyPreferenceStore {
    override suspend fun currencyPreferences(): CurrencyPreferences = CurrencyPreferences()
    override suspend fun updateCurrencyPreferences(preferences: CurrencyPreferences) = Unit
}

internal object NoopAppearancePreferenceStore : AppearancePreferenceStore {
    override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)

    override suspend fun setThemeMode(mode: ThemeMode) = Unit
}

data class SettingsDataUi(
    val retention: SettingsRetentionOption = SettingsRetentionOption.ThirtyDays,
    val pendingAction: SettingsPendingDataAction? = null,
    val confirmedAction: SettingsPendingDataAction? = null,
)

data class SettingsDiagnosticsUi(
    val isExpanded: Boolean = false,
    val copyModel: SettingsDiagnosticsCopyModel? = null,
    val recheckRequestCount: Int = 0,
)

data class SettingsUpdateUi(
    val isChecking: Boolean = false,
    @get:StringRes val statusLabelResId: Int? = null,
    val pendingUpdate: AppUpdateInfo? = null,
    val autoCheckEnabled: Boolean = true,
    val notifyOnUpdateEnabled: Boolean = true,
    val availableUpdate: AppUpdateInfo? = null,
)

data class SettingsAboutUi(
    val versionName: String = "0.1.0",
    val buildName: String = "MVP",
)

data class SettingsUiState(
    val persistentNotification: SettingsPersistentNotificationUi = SettingsPersistentNotificationUi(),
    val alerts: SettingsAlertsUi = SettingsAlertsUi(),
    val refresh: SettingsRefreshUi = SettingsRefreshUi(),
    val currency: SettingsCurrencyUi = SettingsCurrencyUi(),
    val appearance: SettingsAppearanceUi = SettingsAppearanceUi(),
    val data: SettingsDataUi = SettingsDataUi(),
    val diagnostics: SettingsDiagnosticsUi = SettingsDiagnosticsUi(),
    val about: SettingsAboutUi = SettingsAboutUi(),
    val update: SettingsUpdateUi = SettingsUpdateUi(),
    val pendingChoiceDialog: SettingsChoiceDialog? = null,
)

enum class SettingsBackgroundRefreshStatus(@get:StringRes val labelResId: Int) {
    NoCurrentAccount(R.string.settings_refresh_last_result_no_account),
    NoAttempts(R.string.settings_refresh_last_result_no_attempts),
    Success(R.string.settings_refresh_last_result_success),
    Failed(R.string.settings_refresh_last_result_failed),
    Retrying(R.string.settings_refresh_last_result_retrying),
    Skipped(R.string.settings_refresh_last_result_skipped),
    Cancelled(R.string.settings_refresh_last_result_cancelled),
}

fun interface SettingsBackgroundRefreshStatusReader {
    suspend fun latestBackgroundRefreshStatus(): SettingsBackgroundRefreshStatus
}
