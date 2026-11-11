package com.alice.quotarabbit.providers.codex.auth

import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexJwtClaimsCoverageTest {
    private fun jwt(account: String, activeUntil: String, exp: Long = 1999999999L): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".toByteArray())
        val json = """{"exp":$exp,"https://api.openai.com/auth":{
            "chatgpt_account_id":"$account",
            "chatgpt_subscription_active_until":"$activeUntil"
        }}"""
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        return "$header.$payload.signature"
    }

    @Test fun `reads subscription coverage only for the exact matching account`() {
        val token = jwt("account-a", "2026-11-12T17:30:00Z")
        assertEquals(Instant.parse("2026-11-12T17:30:00Z"),
            CodexJwtClaims.subscriptionActiveUntil(token, "account-a"))
        assertNull(CodexJwtClaims.subscriptionActiveUntil(token, "account-b"))
        assertNull(CodexJwtClaims.subscriptionActiveUntil(token, null))
    }

    @Test fun `does not confuse jwt exp with subscription end or accept invalid claims`() {
        assertNull(CodexJwtClaims.subscriptionActiveUntil(jwt("a", ""), "a"))
        assertNull(CodexJwtClaims.subscriptionActiveUntil(jwt("a", "not-a-date"), "a"))
        assertNull(CodexJwtClaims.subscriptionActiveUntil("not-a-jwt", "a"))
        assertNull(CodexJwtClaims.subscriptionActiveUntil("none", "a"))
    }
}
