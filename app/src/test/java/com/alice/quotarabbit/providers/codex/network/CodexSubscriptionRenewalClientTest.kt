package com.alice.quotarabbit.providers.codex.network

import com.alice.quotarabbit.core.network.ProviderHttpClient
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.time.Instant

class CodexSubscriptionRenewalClientTest {
    private val server = MockWebServer()

    @Before fun setup() = server.start()
    @After fun close() = server.close()

    private fun client() = CodexSubscriptionRenewalClient(
        ProviderHttpClient(),
        server.url("/backend-api/accounts/check/v4-2023-04-27").toString(),
        allowInsecureHttpForTests = true,
    )

    @Test fun `only selected account explicit renews at can be shown`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""
            {"accounts":{"different":{"entitlement":{"renews_at":"2030-01-01T00:00:00Z"}},
            "right":{"entitlement":{"renews_at":"2027-02-01T00:00:00Z"},
            "last_active_subscription":{"will_renew":true}}}}
        """.trimIndent()).build())
        val renewal = client().fetchRenewal("synthetic-token", "right")
        assertEquals(Instant.parse("2027-02-01T00:00:00Z"), renewal)
        val request = server.takeRequest()
        assertEquals("right", request.headers["ChatGPT-Account-Id"])
        assertEquals("Bearer synthetic-token", request.headers["Authorization"])
    }

    @Test fun `unknown account cannot fall back to someone elses renewal`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""
            {"accounts":{"different":{"entitlement":{"renews_at":"2030-01-01T00:00:00Z"}}}}
        """.trimIndent()).build())
        assertNull(client().fetchRenewal("synthetic", "missing"))
    }

    @Test fun `denied or canceled subscriptions do not invent a renewal`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("denied").build())
        assertNull(client().fetchRenewal("synthetic", "right"))
        server.enqueue(MockResponse.Builder().code(200).body("""
            {"accounts":{"right":{"entitlement":{"renews_at":"2027-02-01T00:00:00Z"},
            "last_active_subscription":{"will_renew":false}}}}
        """.trimIndent()).build())
        assertNull(client().fetchRenewal("synthetic", "right"))
        assertNull(client().fetchRenewal("synthetic", null))
        assertEquals(2, server.requestCount)
    }

    @Test fun `account expiry is retained as coverage rather than a renewal`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""
            {"accounts":{"right":{"entitlement":{"expires_at":"2027-03-01T05:00:00+09:00"}}}}
        """.trimIndent()).build())
        val dates = client().fetchDates("synthetic", "right")
        assertNull(dates?.renewsAt)
        assertEquals(Instant.parse("2027-02-28T20:00:00Z"), dates?.activeUntil)
    }

    @Test fun `nonrenewing subscription still reports its actual entitlement end`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""
            {"accounts":{"right":{"entitlement":{
              "renews_at":"2027-02-01T00:00:00Z",
              "expires_at":"2027-02-15T00:00:00Z"},
              "last_active_subscription":{"will_renew":false}}}}
        """.trimIndent()).build())
        val value = client().fetchDates("synthetic", "right")
        assertNull(value?.renewsAt)
        assertEquals(Instant.parse("2027-02-15T00:00:00Z"), value?.activeUntil)
    }

    @Test fun `subscription expiry alone is not a renewal`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""
            {"accounts":{"right":{"entitlement":{"expires_at":"2027-02-01T00:00:00Z"}}}}
        """.trimIndent()).build())
        assertNull(client().fetchRenewal("synthetic", "right"))
    }
}
