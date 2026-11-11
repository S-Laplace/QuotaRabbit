package com.alice.quotarabbit.providers.kimi.session

import kotlinx.serialization.Serializable

@Serializable
data class KimiSessionPayload(
    val cookieValue: String,
    val accountId: String? = null,
    val jwtExpiryEpochSeconds: Long? = null,
)
