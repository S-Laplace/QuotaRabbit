package com.alice.quotarabbit.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.alice.quotarabbit.R
import com.alice.quotarabbit.domain.model.LocalAccountId
import android.net.Uri
import com.alice.quotarabbit.ui.auth.AddAccountEntryMode

sealed interface QuotaRabbitRoute {
    val route: String

    @get:StringRes
    val labelResId: Int

    @get:StringRes
    val contentDescriptionResId: Int

    @get:DrawableRes
    val iconResId: Int

    data object Home : QuotaRabbitRoute {
        override val route = "home"
        override val labelResId = R.string.tab_home
        override val contentDescriptionResId = R.string.tab_home_content_description
        override val iconResId = R.drawable.ic_tab_home
    }

    data object Account : QuotaRabbitRoute {
        override val route = "account"
        override val labelResId = R.string.tab_account
        override val contentDescriptionResId = R.string.tab_account_content_description
        override val iconResId = R.drawable.ic_tab_account
    }

    data object AccountDetails : QuotaRabbitRoute {
        const val ACCOUNT_ID = "accountId"
        val pattern = "account_details/{$ACCOUNT_ID}"
        override val route = "account_details"
        override val labelResId = R.string.tab_account
        override val contentDescriptionResId = R.string.tab_account_content_description
        override val iconResId = R.drawable.ic_tab_account

        fun forAccount(id: LocalAccountId): String = "$route/${Uri.encode(id.value)}"
    }

    data object Settings : QuotaRabbitRoute {
        override val route = "settings"
        override val labelResId = R.string.tab_settings
        override val contentDescriptionResId = R.string.tab_settings_content_description
        override val iconResId = R.drawable.ic_tab_settings
    }

    data object AddAccount : QuotaRabbitRoute {
        private const val BASE_ROUTE = "add_account"
        const val ENTRY_MODE_ARG = "entry_mode"
        val routeWithEntryMode = "$BASE_ROUTE/{$ENTRY_MODE_ARG}"

        override val route = BASE_ROUTE
        override val labelResId = R.string.account_add_account
        override val contentDescriptionResId = R.string.account_add_account
        override val iconResId = R.drawable.ic_tab_account

        fun routeFor(entryMode: AddAccountEntryMode): String =
            when (entryMode) {
                is AddAccountEntryMode.ProviderSelection -> route
                else -> "$BASE_ROUTE/${entryMode.routeValue}"
            }
    }

    companion object {
        const val EXTRA_LAUNCH_DESTINATION = "com.alice.quotarabbit.extra.LAUNCH_DESTINATION"

        val bottomTabs = listOf(Home, Account, Settings)

        fun startRouteForLaunchDestination(destination: String?): String =
            when (destination) {
                QuotaRabbitLaunchDestination.AddAccount.value -> AddAccount.route
                QuotaRabbitLaunchDestination.SettingsUpdate.value -> Settings.route
                else -> Home.route
            }
    }
}

enum class QuotaRabbitLaunchDestination(val value: String) {
    Home("home"),
    AddAccount("add_account"),
    SettingsUpdate("settings_update"),
}
