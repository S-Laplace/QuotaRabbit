package com.alice.quotarabbit.providers.kimicode

import com.alice.quotarabbit.data.secure.PayloadCipher
import com.alice.quotarabbit.data.secure.SecureSessionStore
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.refresh.QuotaError
import com.alice.quotarabbit.domain.refresh.RefreshTrigger
import com.alice.quotarabbit.providers.kimicode.mapper.KimiCodeUsageMapper
import com.alice.quotarabbit.providers.kimicode.network.KimiCodeUsageClient
import com.alice.quotarabbit.providers.kimicode.session.KimiCodeSessionPayload
import com.alice.quotarabbit.refresh.ProviderRefreshResult
import com.alice.quotarabbit.refresh.RefreshProvider
import java.time.Clock
import kotlinx.serialization.json.Json

/**
 * Refreshes a Kimi Code account by decrypting its stored API-key payload and fetching usage from
 * the region base URL captured at import. Failures keep the last known good snapshot intact.
 */
class KimiCodeRefreshProvider(
    private val client: KimiCodeUsageClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : RefreshProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun refresh(
        account: ProviderAccount,
        trigger: RefreshTrigger,
    ): ProviderRefreshResult {
        val envelope = sessionStore.load(account.providerId.value, account.localAccountId.value)
            ?: return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "kimi_code_session_missing",
                ),
            )
        val session = try {
            json.decodeFromString<KimiCodeSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "kimi_code_session_decode_failed",
                ),
            )
        }

        val baseUrl = session.apiBaseUrl ?: KimiCodeUsageClient.DEFAULT_BASE_URL
        return when (val result = client.fetchUsage(session.apiKey, baseUrl)) {
            is KimiCodeUsageClient.Result.Failure ->
                ProviderRefreshResult.Failure(result.error)
            is KimiCodeUsageClient.Result.Success ->
                ProviderRefreshResult.Success(
                    KimiCodeUsageMapper.map(
                        dto = result.dto,
                        localAccountId = account.localAccountId,
                        providerAccountId = account.providerAccountId,
                        fetchedAt = clock.instant(),
                        source = trigger.toSnapshotSource(),
                    ),
                )
        }
    }

    private fun RefreshTrigger.toSnapshotSource(): QuotaSnapshotSource = when (this) {
        RefreshTrigger.AppOpen -> QuotaSnapshotSource.AppOpenRefresh
        RefreshTrigger.Manual -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Widget -> QuotaSnapshotSource.WidgetRefresh
        RefreshTrigger.ImportValidation -> QuotaSnapshotSource.ApiKeyImport
        RefreshTrigger.AccountSwitch -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Periodic -> QuotaSnapshotSource.BackgroundRefresh
    }

    companion object {
        val KIMI_CODE_PROVIDER_ID = ProviderId("kimi_code")
    }
}
