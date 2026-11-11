package com.alice.quotarabbit.providers.minimax.auth

import com.alice.quotarabbit.data.secure.PayloadCipher
import com.alice.quotarabbit.data.secure.ProviderSessionEnvelope
import com.alice.quotarabbit.data.secure.SecureSessionStore
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.providers.SessionImporter
import com.alice.quotarabbit.providers.minimax.mapper.MiniMaxUsageMapper
import com.alice.quotarabbit.providers.minimax.network.MiniMaxUsageClient
import com.alice.quotarabbit.providers.minimax.session.MiniMaxSessionPayload
import java.time.Clock
import kotlinx.serialization.json.Json

class MiniMaxSessionImporter(
    private val usageClient: MiniMaxUsageClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : SessionImporter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun importFromApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String?,
    ): Result<QuotaSnapshot> {
        val baseUrl = apiBaseUrl ?: MiniMaxUsageClient.DEFAULT_BASE_URL
        val usageResult = usageClient.fetchUsage(apiKey, baseUrl)

        return when (usageResult) {
            is MiniMaxUsageClient.Result.Failure ->
                Result.failure(
                    RuntimeException(
                        "MiniMax usage fetch failed: ${usageResult.error.safeMessageKey}",
                    ),
                )

            is MiniMaxUsageClient.Result.Success -> {
                val now = clock.instant()
                // Persist the chosen region base URL so later refreshes hit the same platform.
                val payload = MiniMaxSessionPayload(apiKey = apiKey, apiBaseUrl = baseUrl)
                val jsonBytes = json.encodeToString(
                    MiniMaxSessionPayload.serializer(),
                    payload,
                ).encodeToByteArray()

                val encrypted = payloadCipher.encrypt(jsonBytes)
                val envelope = ProviderSessionEnvelope(
                    providerId = MINIMAX_PROVIDER_ID.value,
                    localAccountId = account.localAccountId.value,
                    providerAccountId = account.providerAccountId?.value,
                    schemaVersion = 1,
                    payloadCiphertext = encrypted.ciphertext,
                    payloadNonce = encrypted.nonce,
                    createdAt = now.toString(),
                    updatedAt = now.toString(),
                )

                sessionStore.save(envelope)

                val snapshot = MiniMaxUsageMapper.map(
                    dto = usageResult.dto,
                    localAccountId = account.localAccountId,
                    providerAccountId = account.providerAccountId,
                    fetchedAt = now,
                    source = QuotaSnapshotSource.ApiKeyImport,
                )

                Result.success(snapshot)
            }
        }
    }

    override suspend fun importFromCookie(
        cookieJson: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(
            UnsupportedOperationException("MiniMax does not support cookie auth"),
        )

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(
            UnsupportedOperationException("MiniMax does not support OAuth PKCE auth"),
        )

    private companion object {
        val MINIMAX_PROVIDER_ID = ProviderId("minimax")
    }
}
