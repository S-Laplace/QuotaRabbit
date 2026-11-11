package com.alice.quotarabbit.providers.codex.network

import com.alice.quotarabbit.core.network.ProviderHttpClient
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Exact-account subscription facts, not interchangeable with a guaranteed billing renewal. */
data class CodexSubscriptionDates(
    val renewsAt: Instant? = null,
    val activeUntil: Instant? = null,
)

/**
 * Optional read-only first-party account check. Codex OAuth tokens may get HTTP 403;
 * only a renewal timestamp explicitly reported for the requested account is trusted.
 */
class CodexSubscriptionRenewalClient(
    private val httpClient: ProviderHttpClient,
    private val endpointUrl: String = DEFAULT_ENDPOINT,
    private val allowInsecureHttpForTests: Boolean = false,
) {
    suspend fun fetchRenewal(accessToken: String, accountId: String?): Instant? =
        fetchDates(accessToken, accountId)?.renewsAt

    suspend fun fetchDates(accessToken: String, accountId: String?): CodexSubscriptionDates? {
        val id = accountId?.takeIf { it.isNotBlank() } ?: return null
        val url = endpointUrl.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" && !(allowInsecureHttpForTests && url.scheme == "http")) return null
        val response = try {
            httpClient.get(url.toString(), mapOf(
                "Authorization" to "Bearer $accessToken",
                "ChatGPT-Account-Id" to id,
                "Accept" to "application/json",
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        if (response.statusCode !in 200..299) return null

        // Never select the first/default workspace: it could be a different account.
        return runCatching {
            val root = Json.parseToJsonElement(response.body) as? JsonObject ?: return null
            val accounts = root["accounts"] as? JsonObject ?: root
            val entry = accounts[id] as? JsonObject ?: return null
            val renewFlag = (entry["last_active_subscription"] as? JsonObject)
                ?.get("will_renew") as? JsonPrimitive
            val entitlement = entry["entitlement"] as? JsonObject ?: return null
            val renewal = (entitlement["renews_at"] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val coverageEnd = (entitlement["expires_at"] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val scheduledRenewal = if (renewFlag?.content == "false") null else renewal
            if (scheduledRenewal == null && coverageEnd == null) null
            else CodexSubscriptionDates(renewsAt = scheduledRenewal, activeUntil = coverageEnd)
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27"
    }
}
