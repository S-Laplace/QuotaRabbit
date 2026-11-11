package com.alice.quotarabbit.ui.account

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.alice.quotarabbit.domain.account.isCurrent
import com.alice.quotarabbit.domain.model.AccountStatus
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.settings.NotificationPreferences
import java.time.Instant

enum class AccountContentStatus {
    Unauthenticated,
    HasAccounts,
}

enum class AccountStatusTone {
    Neutral,
    Success,
    Warning,
    Danger,
}

enum class AccountAddAccountIntent {
    LoginToCodex,
}

/** A one-shot request to re-authenticate a specific saved account, consumed by the route to navigate. */
data class AccountReloginRequest(
    val providerId: ProviderId,
    val localAccountId: LocalAccountId,
    /** The provider's account identity (Codex chatgpt_account_id); null when the provider has none. */
    val providerAccountId: String?,
)

data class AccountQuotaSummaryUi(
    val windowId: String,
    @get:StringRes val labelResId: Int,
    val percent: Int?,
    val valueText: String? = null,
    /** Non-null when the balance was converted: shows the provider-native amount. */
    val originalValueText: String? = null,
    /** Non-null for DeepSeek-style granted/topped-up breakdown (native currency). */
    val grantedText: String? = null,
    val toppedUpText: String? = null,
    val resetAt: Instant? = null,
)

/** A per-window quota alert toggle (label + current on/off state). */
data class AccountAlertToggleUi(
    val windowId: String,
    @get:StringRes val labelResId: Int,
    val enabled: Boolean,
)

/** A single model's remaining quota inside a multi-model provider (MiniMax / Antigravity). */
data class AccountModelUi(
    val displayName: String,
    val remainingPercent: Int,
)

/** A group of models sharing a family (Claude / Gemini Pro / Gemini Flash / GPT-OSS). */
data class AccountModelFamilyUi(
    val name: String,
    val models: List<AccountModelUi>,
)

sealed class AccountCreditsUi {
    data class Balance(val amount: Double) : AccountCreditsUi()
    object Unlimited : AccountCreditsUi()
    object Unavailable : AccountCreditsUi()
}

data class AccountItemUi(
    val id: LocalAccountId,
    val displayName: String,
    val avatarInitial: String,
    val avatarColorKey: String,
    val isCurrent: Boolean,
    val status: AccountStatus,
    val tone: AccountStatusTone,
    @get:StringRes val badgeLabelResId: Int,
    @get:StringRes val statusLabelResId: Int,
    @get:StringRes val refreshSummaryResId: Int,
    val lastSuccessfulRefreshAt: Instant?,
    val planType: String?,
    val credits: AccountCreditsUi,
    val isExpanded: Boolean,
    val alertToggles: List<AccountAlertToggleUi>,
    val quotaSummaries: List<AccountQuotaSummaryUi>,
    val modelFamilies: List<AccountModelFamilyUi> = emptyList(),
    @param:DrawableRes val providerIconResId: Int? = null,
    val providerId: ProviderId = ProviderId("unknown"),
    val providerDisplayName: String = "",
    val accentColorId: String? = null,
    val manualRenewalDate: String? = null,
    val bankedResetCount: Int? = null,
    val providerRenewalAt: Instant? = null,
    val subscriptionActiveUntil: Instant? = null,
    val activeUntilFromToken: Boolean = false,
)

data class AccountDeleteConfirmationUi(
    val accountId: LocalAccountId,
    val displayName: String,
)

data class AccountRenameUi(
    val accountId: LocalAccountId,
    val displayName: String,
)

data class AccountUiState(
    val contentStatus: AccountContentStatus,
    val currentAccountId: LocalAccountId?,
    val currentAccount: AccountItemUi?,
    val accounts: List<AccountItemUi>,
    val showAddAccountSheet: Boolean,
    val selectedAddAccountIntent: AccountAddAccountIntent?,
    val pendingReloginRequest: AccountReloginRequest?,
    val pendingDeleteAccount: AccountDeleteConfirmationUi?,
    val pendingRenameAccount: AccountRenameUi?,
    val isRefreshing: Boolean = false,
    val pendingEditAccount: AccountItemUi? = null,
    val isEditSaving: Boolean = false,
    val editFailed: Boolean = false,
    val quotaColorPreferences: NotificationPreferences = NotificationPreferences(),
) {
    companion object {
        val Empty = AccountUiState(
            contentStatus = AccountContentStatus.Unauthenticated,
            currentAccountId = null,
            currentAccount = null,
            accounts = emptyList(),
            showAddAccountSheet = false,
            selectedAddAccountIntent = null,
            pendingReloginRequest = null,
            pendingDeleteAccount = null,
            pendingRenameAccount = null,
        )
    }
}
