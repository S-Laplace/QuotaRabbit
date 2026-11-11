package com.alice.quotarabbit.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.alice.quotarabbit.domain.account.ProviderCurrentSelection
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import java.io.File
import kotlinx.coroutines.flow.first

data class CurrentAccountSelection(
    val providerId: ProviderId,
    val localAccountId: LocalAccountId,
)

interface CurrentAccountReader {
    suspend fun currentAccountSelection(): CurrentAccountSelection?
    suspend fun currentProviderSelections(): List<ProviderCurrentSelection> =
        listOfNotNull(currentAccountSelection()?.let { ProviderCurrentSelection(it.providerId, it.localAccountId, 0L) })
}

interface CurrentAccountStore : CurrentAccountReader {
    suspend fun updateCurrentAccountSelection(selection: CurrentAccountSelection?)
    suspend fun clearProviderSelectionIfMatches(selection: CurrentAccountSelection) = Unit
}

class CurrentAccountPreferences(
    internal val dataStore: DataStore<Preferences>,
) : CurrentAccountStore {
    override suspend fun currentAccountSelection(): CurrentAccountSelection? {
        val preferences = dataStore.data.first()
        val providerId = preferences[Keys.CURRENT_PROVIDER_ID]?.takeIf { it.isNotBlank() }
        val localAccountId = preferences[Keys.CURRENT_LOCAL_ACCOUNT_ID]?.takeIf { it.isNotBlank() }

        return if (providerId != null && localAccountId != null) {
            CurrentAccountSelection(
                providerId = ProviderId(providerId),
                localAccountId = LocalAccountId(localAccountId),
            )
        } else {
            null
        }
    }

    override suspend fun currentProviderSelections(): List<ProviderCurrentSelection> {
        val preferences = dataStore.data.first()
        val saved = preferences.asMap().keys.mapNotNull { key ->
            val provider = key.name.removePrefix(PROVIDER_SELECTION_PREFIX)
            if (provider == key.name) return@mapNotNull null
            val accountId = preferences[stringPreferencesKey(key.name)]?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            ProviderCurrentSelection(
                providerId = ProviderId(provider),
                localAccountId = LocalAccountId(accountId),
                selectionOrder = preferences[longPreferencesKey(PROVIDER_ORDER_PREFIX + provider)] ?: 0L,
            )
        }
        if (saved.isNotEmpty()) return saved
        val provider = preferences[Keys.CURRENT_PROVIDER_ID]?.takeIf { it.isNotBlank() }
        val account = preferences[Keys.CURRENT_LOCAL_ACCOUNT_ID]?.takeIf { it.isNotBlank() }
        return if (provider != null && account != null) {
            listOf(ProviderCurrentSelection(ProviderId(provider), LocalAccountId(account), 0L))
        } else emptyList()
    }

    override suspend fun updateCurrentAccountSelection(selection: CurrentAccountSelection?) {
        dataStore.edit { preferences ->
            if (selection == null) {
                preferences.remove(Keys.CURRENT_PROVIDER_ID)
                preferences.remove(Keys.CURRENT_LOCAL_ACCOUNT_ID)
            } else {
                // Preserve pre-upgrade global selection when the first other provider is selected.
                val previousProvider = preferences[Keys.CURRENT_PROVIDER_ID]
                val previousAccount = preferences[Keys.CURRENT_LOCAL_ACCOUNT_ID]
                if (!previousProvider.isNullOrBlank() && !previousAccount.isNullOrBlank() &&
                    preferences[stringPreferencesKey(PROVIDER_SELECTION_PREFIX + previousProvider)] == null
                ) {
                    preferences[stringPreferencesKey(PROVIDER_SELECTION_PREFIX + previousProvider)] = previousAccount
                    preferences[longPreferencesKey(PROVIDER_ORDER_PREFIX + previousProvider)] = 0L
                }
                val sequence = (preferences[Keys.CURRENT_SELECTION_SEQUENCE] ?: 0L) + 1L
                preferences[Keys.CURRENT_SELECTION_SEQUENCE] = sequence
                preferences[stringPreferencesKey(PROVIDER_SELECTION_PREFIX + selection.providerId.value)] = selection.localAccountId.value
                preferences[longPreferencesKey(PROVIDER_ORDER_PREFIX + selection.providerId.value)] = sequence
                preferences[Keys.CURRENT_PROVIDER_ID] = selection.providerId.value
                preferences[Keys.CURRENT_LOCAL_ACCOUNT_ID] = selection.localAccountId.value
            }
        }
    }

    override suspend fun clearProviderSelectionIfMatches(selection: CurrentAccountSelection) {
        dataStore.edit { preferences ->
            val accountKey = stringPreferencesKey(PROVIDER_SELECTION_PREFIX + selection.providerId.value)
            if (preferences[accountKey] == selection.localAccountId.value) {
                preferences.remove(accountKey)
                preferences.remove(longPreferencesKey(PROVIDER_ORDER_PREFIX + selection.providerId.value))
            }
        }
    }

    companion object {
        private const val PROVIDER_SELECTION_PREFIX = "current_provider_account_"
        private const val PROVIDER_ORDER_PREFIX = "current_provider_order_"
        fun create(file: File): CurrentAccountPreferences =
            CurrentAccountPreferences(
                PreferenceDataStoreFactory.create(
                    produceFile = { file },
                ),
            )
    }

    object Keys {
        val CURRENT_PROVIDER_ID = stringPreferencesKey("current_provider_id")
        val CURRENT_LOCAL_ACCOUNT_ID = stringPreferencesKey("current_local_account_id")
        val CURRENT_SELECTION_SEQUENCE = longPreferencesKey("current_selection_sequence")
    }
}
