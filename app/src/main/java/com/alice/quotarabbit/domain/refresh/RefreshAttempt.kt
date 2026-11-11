package com.alice.quotarabbit.domain.refresh

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.RefreshAttemptId
import java.time.Instant

enum class RefreshTrigger {
    AppOpen,
    Manual,
    Widget,
    // Legacy trigger kept so older local refresh attempts can still be decoded.
    ImportValidation,
    AccountSwitch,
    Periodic,
}

enum class RefreshAttemptStatus { Success, Failed, Skipped, Cancelled }

data class RefreshAttempt(
    val attemptId: RefreshAttemptId,
    val providerId: ProviderId,
    val localAccountId: LocalAccountId,
    val trigger: RefreshTrigger,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: RefreshAttemptStatus,
    val errorCode: String?,
    val httpStatus: Int?,
    val retryable: Boolean?,
    val userActionRequired: Boolean?,
    val diagnosticsDigest: String?,
)
