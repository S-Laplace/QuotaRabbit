package com.alice.quotarabbit.domain.account

import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId

/** Exactly one selected account per provider; larger sequence means a later Set Current action. */
data class ProviderCurrentSelection(
    val providerId: ProviderId,
    val localAccountId: LocalAccountId,
    val selectionOrder: Long,
)

fun List<ProviderCurrentSelection>.isCurrent(providerId: ProviderId, localAccountId: LocalAccountId): Boolean =
    any { it.providerId == providerId && it.localAccountId == localAccountId }

/** Show every selected account first, latest selection first; leave all unselected rows stable. */
fun <T> List<T>.sortedByCurrentSelection(
    selections: List<ProviderCurrentSelection>,
    providerId: (T) -> ProviderId,
    localAccountId: (T) -> LocalAccountId,
): List<T> {
    val byProvider = selections.associateBy { it.providerId }
    fun selectionOrder(item: T): Long? =
        byProvider[providerId(item)]?.takeIf { it.localAccountId == localAccountId(item) }?.selectionOrder
    return withIndex().sortedWith(
        compareByDescending<IndexedValue<T>> { selectionOrder(it.value) != null }
            .thenByDescending { selectionOrder(it.value) ?: Long.MIN_VALUE }
            .thenBy { it.index },
    ).map { it.value }
}
