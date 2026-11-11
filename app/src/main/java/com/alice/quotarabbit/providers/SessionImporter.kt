package com.alice.quotarabbit.providers

import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.quota.QuotaSnapshot

interface SessionImporter {
    /**
     * @param apiBaseUrl region API base URL chosen by the user; null means use the provider default.
     *   Only region-aware API-key providers (z.ai, MiniMax) honour it; others ignore it.
     */
    suspend fun importFromApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String? = null,
    ): Result<QuotaSnapshot>
    suspend fun importFromCookie(cookieJson: String, account: ProviderAccount): Result<QuotaSnapshot>
    suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot>
}
