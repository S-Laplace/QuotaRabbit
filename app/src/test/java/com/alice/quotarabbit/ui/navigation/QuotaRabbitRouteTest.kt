package com.alice.quotarabbit.ui.navigation

import com.alice.quotarabbit.R
import com.alice.quotarabbit.ui.auth.AddAccountEntryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QuotaRabbitRouteTest {
    @Test
    fun `bottom tabs are home account settings in order`() {
        assertEquals(
            listOf(QuotaRabbitRoute.Home, QuotaRabbitRoute.Account, QuotaRabbitRoute.Settings),
            QuotaRabbitRoute.bottomTabs,
        )
        assertEquals(
            listOf("home", "account", "settings"),
            QuotaRabbitRoute.bottomTabs.map { it.route },
        )
    }

    @Test
    fun `bottom tabs declare label content description and icon resources`() {
        QuotaRabbitRoute.bottomTabs.forEach { route ->
            assertNotEquals("label resource must be set for ${route.route}", 0, route.labelResId)
            assertNotEquals(
                "content description resource must be set for ${route.route}",
                0,
                route.contentDescriptionResId,
            )
            assertNotEquals("icon resource must be set for ${route.route}", 0, route.iconResId)
        }

        assertEquals(R.string.tab_home, QuotaRabbitRoute.Home.labelResId)
        assertEquals(R.string.tab_home_content_description, QuotaRabbitRoute.Home.contentDescriptionResId)
        assertEquals(R.drawable.ic_tab_home, QuotaRabbitRoute.Home.iconResId)

        assertEquals(R.string.tab_account, QuotaRabbitRoute.Account.labelResId)
        assertEquals(R.string.tab_account_content_description, QuotaRabbitRoute.Account.contentDescriptionResId)
        assertEquals(R.drawable.ic_tab_account, QuotaRabbitRoute.Account.iconResId)

        assertEquals(R.string.tab_settings, QuotaRabbitRoute.Settings.labelResId)
        assertEquals(R.string.tab_settings_content_description, QuotaRabbitRoute.Settings.contentDescriptionResId)
        assertEquals(R.drawable.ic_tab_settings, QuotaRabbitRoute.Settings.iconResId)
    }

    @Test
    fun `widget launch destination resolves add account route`() {
        assertEquals(
            QuotaRabbitRoute.Home.route,
            QuotaRabbitRoute.startRouteForLaunchDestination(null),
        )
        assertEquals(
            QuotaRabbitRoute.Home.route,
            QuotaRabbitRoute.startRouteForLaunchDestination(QuotaRabbitLaunchDestination.Home.value),
        )
        assertEquals(
            QuotaRabbitRoute.AddAccount.route,
            QuotaRabbitRoute.startRouteForLaunchDestination(QuotaRabbitLaunchDestination.AddAccount.value),
        )
    }

    @Test
    fun `add account routes only preserve device code login entry mode`() {
        assertEquals(
            "add_account/login",
            QuotaRabbitRoute.AddAccount.routeFor(AddAccountEntryMode.LoginToCodex),
        )
        assertEquals(
            AddAccountEntryMode.LoginToCodex,
            AddAccountEntryMode.fromRouteValue("login"),
        )
        assertEquals(
            AddAccountEntryMode.Choose,
            AddAccountEntryMode.fromRouteValue("import"),
        )
        assertEquals(
            AddAccountEntryMode.Choose,
            AddAccountEntryMode.fromRouteValue("unexpected"),
        )
    }

    @Test
    fun `device code entry mode round-trips through the route value`() {
        val grok = com.alice.quotarabbit.domain.model.ProviderId("grok")
        val mode = AddAccountEntryMode.DeviceCodeLogin(grok)
        assertEquals("devicecode:grok", mode.routeValue)
        assertEquals(mode, AddAccountEntryMode.fromRouteValue(mode.routeValue))

        val relogin = AddAccountEntryMode.DeviceCodeLogin(
            providerId = grok,
            reloginAccountId = com.alice.quotarabbit.domain.model.LocalAccountId("acct-1"),
        )
        assertEquals("devicecode:grok:acct-1", relogin.routeValue)
        assertEquals(relogin, AddAccountEntryMode.fromRouteValue(relogin.routeValue))

        val reloginWithExpected = AddAccountEntryMode.DeviceCodeLogin(
            providerId = grok,
            reloginAccountId = com.alice.quotarabbit.domain.model.LocalAccountId("acct-1"),
            expectedProviderAccountId = "sub-789",
        )
        assertEquals("devicecode:grok:acct-1:sub-789", reloginWithExpected.routeValue)
        assertEquals(reloginWithExpected, AddAccountEntryMode.fromRouteValue(reloginWithExpected.routeValue))
    }
}
