package com.alice.quotarabbit.providers.cursor.auth

import com.alice.quotarabbit.data.secure.PayloadCipher
import com.alice.quotarabbit.data.secure.ProviderSessionEnvelope
import com.alice.quotarabbit.data.secure.SecureSessionStore
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.providers.SessionImporter
import com.alice.quotarabbit.providers.cursor.mapper.CursorUsageMapper
import com.alice.quotarabbit.providers.cursor.network.CursorUsageClient
import com.alice.quotarabbit.providers.cursor.session.CursorSessionPayload
import java.time.Clock
import kotlinx.serialization.json.Json

class CursorSessionImporter(
    private val usageClient: CursorUsageClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : SessionImporter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun importFromCookie(
        cookieJson: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> {
        val usageResult = usageClient.fetchUsage(cookieJson)

        return when (usageResult) {
            is CursorUsageClient.Result.Failure ->
                Result.failure(
                    RuntimeException(
                        "Cursor usage fetch failed: ${usageResult.error.safeMessageKey}",
                    ),
                )

            is CursorUsageClient.Result.Success -> {
                val now = clock.instant()
                val payload = CursorSessionPayload(cookieJson)
                val jsonBytes = json.encodeToString(
                    CursorSessionPayload.serializer(),
                    payload,
                ).encodeToByteArray()

                val encrypted = payloadCipher.encrypt(jsonBytes)
                val envelope = ProviderSessionEnvelope(
                    providerId = CURSOR_PROVIDER_ID.value,
                    localAccountId = account.localAccountId.value,
                    providerAccountId = account.providerAccountId?.value,
                    schemaVersion = 1,
                    payloadCiphertext = encrypted.ciphertext,
                    payloadNonce = encrypted.nonce,
                    createdAt = now.toString(),
                    updatedAt = now.toString(),
                )

                sessionStore.save(envelope)

                val snapshot = CursorUsageMapper.map(
                    dto = usageResult.dto,
                    localAccountId = account.localAccountId,
                    providerAccountId = account.providerAccountId,
                    fetchedAt = now,
                    source = QuotaSnapshotSource.CookieAuth,
                )

                Result.success(snapshot)
            }
        }
    }

    override suspend fun importFromApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String?,
    ): Result<QuotaSnapshot> =
        Result.failure(
            UnsupportedOperationException("Cursor does not support API key auth"),
        )

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(
            UnsupportedOperationException("Cursor does not support OAuth PKCE auth"),
        )

    private companion object {
        val CURSOR_PROVIDER_ID = ProviderId("cursor")
    }
}
