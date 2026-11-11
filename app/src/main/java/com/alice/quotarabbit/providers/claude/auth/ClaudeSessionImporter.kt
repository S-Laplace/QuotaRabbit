package com.alice.quotarabbit.providers.claude.auth

import com.alice.quotarabbit.data.secure.PayloadCipher
import com.alice.quotarabbit.data.secure.ProviderSessionEnvelope
import com.alice.quotarabbit.data.secure.SecureSessionStore
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.quota.QuotaSnapshot
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.providers.SessionImporter
import com.alice.quotarabbit.providers.claude.mapper.ClaudeUsageMapper
import com.alice.quotarabbit.providers.claude.network.ClaudeUsageClient
import com.alice.quotarabbit.providers.claude.session.ClaudeSessionPayload
import com.alice.quotarabbit.providers.common.auth.OAuthTokenClient
import java.time.Clock
import kotlinx.serialization.json.Json

class ClaudeSessionImporter(
    private val tokenClient: OAuthTokenClient,
    private val client: ClaudeUsageClient,
    private val sessionStore: SecureSessionStore,
    private val payloadCipher: PayloadCipher,
    private val clock: Clock = Clock.systemUTC(),
) : SessionImporter {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun importFromOAuthPkce(
        code: String,
        verifier: String,
        redirectUri: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> {
        // The interceptor passes the callback as `code#state` (Hermes / Claude Code format); the
        // Anthropic token endpoint validates `state`, so split it back out for the exchange.
        val rawCode = code.substringBefore('#')
        val state = code.substringAfter('#', "").takeIf { it.isNotBlank() }
        // The authorization code is not a bearer token: exchange it for real tokens first.
        val tokens = when (val result = tokenClient.exchangeAuthorizationCode(rawCode, verifier, redirectUri, state)) {
            is OAuthTokenClient.Result.Failure ->
                return Result.failure(RuntimeException("Claude OAuth token exchange failed: ${result.error}"))
            is OAuthTokenClient.Result.Success -> result.tokens
        }

        return when (val usageResult = client.fetchUsage(tokens.accessToken)) {
            is ClaudeUsageClient.Result.Failure ->
                Result.failure(RuntimeException("Claude usage fetch failed: ${usageResult.error.safeMessageKey}"))

            is ClaudeUsageClient.Result.Success -> {
                val now = clock.instant()
                val payload = ClaudeSessionPayload(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    tokenExpiresAtEpochSeconds = tokens.expiresInSeconds?.let { now.epochSecond + it },
                )
                val jsonBytes = json.encodeToString(ClaudeSessionPayload.serializer(), payload).encodeToByteArray()
                val encrypted = payloadCipher.encrypt(jsonBytes)
                sessionStore.save(
                    ProviderSessionEnvelope(
                        providerId = CLAUDE_PROVIDER_ID.value,
                        localAccountId = account.localAccountId.value,
                        providerAccountId = account.providerAccountId?.value,
                        schemaVersion = 1,
                        payloadCiphertext = encrypted.ciphertext,
                        payloadNonce = encrypted.nonce,
                        createdAt = now.toString(),
                        updatedAt = now.toString(),
                    ),
                )
                Result.success(
                    ClaudeUsageMapper.map(
                        dto = usageResult.dto,
                        localAccountId = account.localAccountId,
                        providerAccountId = account.providerAccountId,
                        fetchedAt = now,
                        source = QuotaSnapshotSource.OAuthPkceLogin,
                    ),
                )
            }
        }
    }

    override suspend fun importFromApiKey(
        apiKey: String,
        account: ProviderAccount,
        apiBaseUrl: String?,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("Claude does not support API key auth"))

    override suspend fun importFromCookie(
        cookieJson: String,
        account: ProviderAccount,
    ): Result<QuotaSnapshot> =
        Result.failure(UnsupportedOperationException("Claude does not support cookie auth"))

    private companion object {
        val CLAUDE_PROVIDER_ID = ProviderId("claude")
    }
}
