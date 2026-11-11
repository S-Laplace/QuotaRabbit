package com.alice.quotarabbit.domain.account

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.domain.model.ProviderId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Nullable color means automatic account identity color, never the old avatarColorKey. */
val ACCOUNT_ACCENT_COLORS: List<String> = listOf("teal", "violet", "amber", "rose", "slate", "green", "red", "brown")

data class AccountEditInput(
    val nickname: String,
    val accentColorId: String?,
    /** ISO-8601 LocalDate; deliberately has no timezone or moment-in-time semantics. */
    val manualRenewalDate: String?,
) {
    fun validated(): AccountEditInput {
        val safeName = nickname.trim()
        require(safeName.isNotEmpty() && safeName.length <= 100) { "Invalid nickname" }
        require(accentColorId == null || accentColorId in ACCOUNT_ACCENT_COLORS) { "Invalid color" }
        manualRenewalDate?.let {
            require(it.length == 10 && runCatching {
                LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE).toString() == it
            }.getOrDefault(false)) { "Invalid renewal date" }
        }
        return copy(nickname = safeName)
    }
}

fun interface AccountEditUseCase {
    suspend fun editAccount(providerId: ProviderId, localAccountId: LocalAccountId, input: AccountEditInput): ProviderAccount?
}
