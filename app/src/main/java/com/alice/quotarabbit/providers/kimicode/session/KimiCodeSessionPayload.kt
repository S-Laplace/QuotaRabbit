package com.alice.quotarabbit.providers.kimicode.session

import kotlinx.serialization.Serializable

/** Encrypted at rest via [com.alice.quotarabbit.data.secure.PayloadCipher]; never logged. */
@Serializable
data class KimiCodeSessionPayload(
    val apiKey: String,
    /** Region API base URL chosen at import. Null = legacy session → default (China) base. */
    val apiBaseUrl: String? = null,
)
