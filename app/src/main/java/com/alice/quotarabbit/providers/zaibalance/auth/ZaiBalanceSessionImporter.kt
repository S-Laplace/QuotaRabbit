package com.alice.quotarabbit.providers.zaibalance.auth

import com.alice.quotarabbit.data.secure.PayloadCipher
import com.alice.quotarabbit.data.secure.ProviderSessionEnvelope
import com.alice.quotarabbit.data.secure.SecureSessionStore
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.providers.SessionImporter
import com.alice.quotarabbit.providers.zaibalance.mapper.ZaiBalanceMapper
import com.alice.quotarabbit.providers.zaibalance.network.ZaiBalanceClient
import com.alice.quotarabbit.providers.zaibalance.session.ZaiBalanceSessionPayload
import java.time.Clock
import kotlinx.serialization.json.Json

class ZaiBalanceSessionImporter(
    private val client: ZaiBalanceClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : SessionImporter {
    private val json = Json { ignoreUnknownKeys = true }

    // The balance endpoint is China-only (open.bigmodel.cn); apiBaseUrl is ignored.
    override suspend fun importFromApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String?,
    ): Result<QuotaSnapshot> {
        return when (val result = client.fetchBalance(apiKey)) {
            is ZaiBalanceClient.Result.Failure ->
                Result.failure(
                    RuntimeException("z.ai API balance import failed: ${result.error.diagnosticsDigest}"),
                )
            is ZaiBalanceClient.Result.Success -> {
                val now = clock.instant()
                val nowStr = now.toString()
                val payload = ZaiBalanceSessionPayload(apiKey = apiKey)
                val jsonBytes = json.encodeToString(ZaiBalanceSessionPayload.serializer(), payload)
                    .encodeToByteArray()
                val encrypted = payloadCipher.encrypt(jsonBytes)
                val envelope = ProviderSessionEnvelope(
                    providerId = account.providerId.value,
                    localAccountId = account.localAccountId.value,
                    providerAccountId = account.providerAccountId?.value,
                    schemaVersion = 1,
                    payloadCiphertext = encrypted.ciphertext,
                    payloadNonce = encrypted.nonce,
                    createdAt = nowStr,
                    updatedAt = nowStr,
                )
                sessionStore.save(envelope)
                val snapshot = ZaiBalanceMapper.map(
                    dto = result.dto,
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
        Result.failure(UnsupportedOperationException("z.ai API does not support cookie auth"))

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("z.ai API does not support OAuth PKCE auth"))
}
