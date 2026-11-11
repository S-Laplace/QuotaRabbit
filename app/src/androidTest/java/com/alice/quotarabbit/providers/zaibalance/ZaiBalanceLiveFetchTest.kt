package com.alice.quotarabbit.providers.zaibalance

import androidx.test.platform.app.InstrumentationRegistry
import com.alice.quotarabbit.core.network.ProviderHttpClient
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.quota.QuotaSnapshotSource
import com.alice.quotarabbit.domain.quota.QuotaWindowDisplayKind
import com.alice.quotarabbit.providers.zaibalance.mapper.ZaiBalanceMapper
import com.alice.quotarabbit.providers.zaibalance.network.ZaiBalanceClient
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live, manual-only check: hits the real bigmodel endpoint with an API key passed at runtime. Skips
 * automatically when no key is supplied, so it never runs in CI. The key is never committed.
 * Run: ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.zai_api_key="<id.secret>"
 */
class ZaiBalanceLiveFetchTest {
    @Test
    fun fetchesAndMapsRealBalance() = runBlocking {
        val apiKey = InstrumentationRegistry.getArguments().getString("zai_api_key")
        assumeTrue("no zai_api_key arg supplied; skipping live test", !apiKey.isNullOrBlank())

        val result = ZaiBalanceClient(ProviderHttpClient()).fetchBalance(apiKey!!)
        assertTrue("expected Success, got $result", result is ZaiBalanceClient.Result.Success)

        val snapshot = ZaiBalanceMapper.map(
            dto = (result as ZaiBalanceClient.Result.Success).dto,
            localAccountId = LocalAccountId("zaibal-live"),
            providerAccountId = null,
            fetchedAt = Instant.now(),
            source = QuotaSnapshotSource.ApiKeyImport,
        )
        assertEquals(QuotaWindowDisplayKind.Balance, snapshot.windows.single().displayKind)
    }
}
