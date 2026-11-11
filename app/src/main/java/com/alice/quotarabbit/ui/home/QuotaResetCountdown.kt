package com.alice.quotarabbit.ui.home

import java.time.Duration
import java.time.Instant

/** Displays the provider's reset timestamp, never claims that an elapsed reset restored quota. */
internal fun quotaResetMinutes(resetAt: Instant?, now: Instant): Long? {
    if (resetAt == null) return null
    val duration = Duration.between(now, resetAt)
    if (duration.isNegative || duration.isZero) return 0
    val minutes = duration.toMinutes()
    return minutes + if (duration.minusMinutes(minutes).isZero) 0 else 1
}
