package com.alice.quotarabbit.providers.kimi

import com.alice.quotarabbit.data.secure.PayloadCipher
import com.alice.quotarabbit.data.secure.SecureSessionStore
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.refresh.QuotaError
import com.alice.quotarabbit.domain.refresh.RefreshTrigger
import com.alice.quotarabbit.providers.kimi.mapper.KimiQuotaMapper
import com.alice.quotarabbit.providers.kimi.network.KimiQuotaClient
import com.alice.quotarabbit.providers.kimi.session.KimiSessionPayload
import com.alice.quotarabbit.refresh.ProviderRefreshResult
import com.alice.quotarabbit.refresh.RefreshProvider
import kotlinx.serialization.json.Json
import java.time.Clock

class KimiRefreshProvider(
    private val client: KimiQuotaClient,
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
                    diagnosticsDigest = "kimi_session_missing",
                ),
            )
        val session = try {
            json.decodeFromString<KimiSessionPayload>(
                payloadCipher.decrypt(envelope.payloadCiphertext, envelope.payloadNonce).decodeToString(),
            )
        } catch (_: Exception) {
            return ProviderRefreshResult.Failure(
                QuotaError.AuthRequired(
                    httpStatus = null,
                    diagnosticsDigest = "kimi_session_decode_failed",
                ),
            )
        }

        // Pre-detect JWT expiry
        session.jwtExpiryEpochSeconds?.let { expiry ->
            val nowEpoch = clock.instant().epochSecond
            if (expiry <= nowEpoch) {
                return ProviderRefreshResult.Failure(
                    QuotaError.AuthRequired(
                        httpStatus = null,
                        diagnosticsDigest = "kimi_jwt_expired",
                    ),
                )
            }
        }

        return when (val result = client.fetchQuota(session.cookieValue)) {
            is KimiQuotaClient.Result.Failure ->
                ProviderRefreshResult.Failure(result.error)
            is KimiQuotaClient.Result.Success ->
                ProviderRefreshResult.Success(
                    KimiQuotaMapper.map(
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
        RefreshTrigger.ImportValidation -> QuotaSnapshotSource.CookieAuth
        RefreshTrigger.AccountSwitch -> QuotaSnapshotSource.ManualRefresh
        RefreshTrigger.Periodic -> QuotaSnapshotSource.BackgroundRefresh
    }

    companion object {
        val KIMI_PROVIDER_ID = ProviderId("kimi")
    }
}
