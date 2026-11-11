package com.alice.quotarabbit.widget

/** 微件可展示字段上限（与最大尺寸 4×2 的容量一致）。 */
const val WIDGET_MAX_FIELDS = 4
const val WIDGET_MAX_ACCOUNTS = 4

@kotlinx.serialization.Serializable
data class WidgetAccountSelection(val providerId: String, val localAccountId: String)

data class WidgetQuotaConfiguration(
    val providerId: String? = null,
    val localAccountId: String? = null,
    // 已选要展示的窗口 id，按供应商天然顺序，最多 WIDGET_MAX_FIELDS 个。
    val selectedWindowIds: List<String> = emptyList(),
    val selectedAccounts: List<WidgetAccountSelection> = emptyList(),
) {
    val effectiveAccounts: List<WidgetAccountSelection>
        get() = selectedAccounts.filter { it.providerId.isNotBlank() && it.localAccountId.isNotBlank() }
            .distinct().take(WIDGET_MAX_ACCOUNTS).ifEmpty {
                if (hasAccountOverride) listOf(WidgetAccountSelection(providerId.orEmpty(), localAccountId.orEmpty()))
                else emptyList()
            }

    val hasAccountOverride: Boolean
        get() = !providerId.isNullOrBlank() && !localAccountId.isNullOrBlank()

    val isDefault: Boolean
        get() = effectiveAccounts.isEmpty() && selectedWindowIds.isEmpty()

    fun accountMatches(providerId: String, localAccountId: String): Boolean =
        effectiveAccounts.any { it.providerId == providerId && it.localAccountId == localAccountId }
}

internal fun toggleWidgetAccountSelection(
    current: List<WidgetAccountSelection>,
    account: WidgetAccountSelection,
): List<WidgetAccountSelection> = when {
    account in current -> current - account
    current.size >= WIDGET_MAX_ACCOUNTS -> current
    else -> current + account
}

internal enum class WidgetConfigurationSection {
    Account,
    DisplayFields,
}

fun interface WidgetQuotaStateLoader {
    suspend fun loadWidgetQuotaState(configuration: WidgetQuotaConfiguration): WidgetQuotaState
}
