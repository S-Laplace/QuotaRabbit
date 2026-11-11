package com.alice.quotarabbit.providers.deepseek.session

import kotlinx.serialization.Serializable

@Serializable
data class DeepSeekSessionPayload(val apiKey: String)
