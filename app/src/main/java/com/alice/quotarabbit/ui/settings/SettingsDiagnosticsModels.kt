package com.alice.quotarabbit.ui.settings



data class DiagnosticsAccountSummary(
    val providerId: String,
    val accountIdHash: String,
    val status: String,
    val lastAttemptStatus: String,
    val lastAttemptErrorCode: String? = null,
    val lastAttemptAt: String? = null,
    val lastSuccessfulRefreshAt: String? = null,
    val latestSnapshotAt: String? = null,
)

data class DiagnosticsAccountAlertConfig(
    val providerId: String,
    val accountIdHash: String,
    val enabledWindowIds: List<String>,
)

/** 结构化 WorkManager 诊断；由采集层填充，由 buildDiagnosticsCopyModel 渲染。 */
data class WorkManagerDiagnostics(
    val periodicStatus: String,
    val periodicRunAttemptCount: Int? = null,
    val periodicNextScheduleAtMillis: Long? = null,
    val periodicStopReason: String? = null,
    val onceStatus: String? = null,
)

data class SettingsDiagnosticsInput(
    val appVersion: String = "0.1.0",
    val buildType: String = "debug",
    val androidSdk: String? = null,
    val providerId: String = "codex",
    val accountDiagnosticId: String = "not_connected",
    val currentAccountSelectionStatus: String = "unknown",
    val accountStatus: String? = null,
    val accountCount: Int? = null,
    val sessionEnvelopeStatus: String? = null,
    val sessionProviderAccountIdStatus: String? = null,
    val currentState: String = "noData",
    val latestSnapshotStatus: String? = null,
    val latestSnapshotSource: String? = null,
    val latestSnapshotFetchedAt: String? = null,
    val latestSnapshotDigestStatus: String? = null,
    val lastSuccessfulRefreshAt: String = "unavailable",
    val lastAttemptStatus: String = "none",
    val lastAttemptTrigger: String? = null,
    val lastAttemptStartedAt: String? = null,
    val lastAttemptFinishedAt: String? = null,
    val safeErrorCode: String? = null,
    val httpStatus: Int? = null,
    val retryable: Boolean? = null,
    val userActionRequired: Boolean? = null,
    val diagnosticsDigest: String? = null,
    val deviceCodeLoginStatus: String? = null,
    val deviceCodeLoginAttemptId: String? = null,
    val deviceCodeLoginSafeErrorCode: String? = null,
    val deviceCodeLoginVerificationUriStatus: String? = null,
    val deviceCodeLoginPollIntervalSeconds: Int? = null,
    val deviceCodeLoginExpiresAt: String? = null,
    val workManagerStatus: String = "not_wired",
    val notificationPermissionStatus: String = "unknown",
    // A: 参考时刻
    val generatedAtMillis: Long? = null,
    // B: 运行环境
    val androidRelease: String? = null,
    val deviceModel: String? = null,
    val locale: String? = null,
    val batteryOptimizationIgnored: Boolean? = null,
    val backgroundRestricted: String? = null,
    val networkType: String? = null,
    val dataSaver: String? = null,
    val appFirstInstallAt: String? = null,
    val appLastUpdateAt: String? = null,
    // C: WorkManager 加深
    val workManagerRunAttemptCount: Int? = null,
    val workManagerNextScheduleAt: String? = null,
    val workManagerStopReason: String? = null,
    val onceWorkManagerStatus: String? = null,
    // D: 连续失败
    val consecutiveFailures: Int? = null,
    // E: 全账号摘要
    val accountSummaries: List<DiagnosticsAccountSummary> = emptyList(),
    // F: App 配置
    val roomSchemaVersion: Int? = null,
    val retentionDays: String? = null,
    val refreshIntervalMinutes: Int? = null,
    val statusNotificationEnabled: Boolean? = null,
    val quotaAlertsEnabled: Boolean? = null,
    val alertThresholds: String? = null,
    val accountAlertConfigs: List<DiagnosticsAccountAlertConfig> = emptyList(),
    val widgetCount: Int? = null,
    val unsafeDetails: String? = null,
)

data class SettingsDiagnosticsCopyModel(val text: String)

fun interface SettingsDiagnosticsReader {
    suspend fun diagnosticsInput(): SettingsDiagnosticsInput
}

object DefaultSettingsDiagnosticsReader : SettingsDiagnosticsReader {
    override suspend fun diagnosticsInput(): SettingsDiagnosticsInput = SettingsDiagnosticsInput()
}
