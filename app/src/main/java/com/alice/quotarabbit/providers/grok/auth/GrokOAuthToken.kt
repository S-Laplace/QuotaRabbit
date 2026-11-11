package com.alice.quotarabbit.providers.grok.auth

data class GrokOAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String?,
    val expiresAtEpochSeconds: Long?,
) {
    override fun toString(): String =
        "GrokOAuthToken(" +
            "accessToken=[REDACTED], " +
            "refreshToken=[REDACTED], " +
            "idToken=[REDACTED], " +
            "expiresAtEpochSeconds=$expiresAtEpochSeconds" +
            ")"
}
