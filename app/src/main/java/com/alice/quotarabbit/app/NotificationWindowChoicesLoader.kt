package com.alice.quotarabbit.app

import com.alice.quotarabbit.data.local.dao.QuotaSnapshotDao
import com.alice.quotarabbit.data.preferences.CurrentAccountReader
import com.alice.quotarabbit.data.repository.toDomain
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.QuotaWindowId
import com.alice.quotarabbit.domain.quota.QuotaWindowAvailability

/** A selectable notification window identified by its stable windowId. */
data class NotificationWindowChoice(
    val windowId: QuotaWindowId,
    val limitWindowSeconds: Int? = null,
)

fun interface NotificationWindowChoicesLoader {
    /** Latest snapshot windows for [providerId]/[localAccountId]; null args -> current account. */
    suspend fun windowChoices(providerId: ProviderId?, localAccountId: LocalAccountId?): List<NotificationWindowChoice>
}

class DefaultNotificationWindowChoicesLoader(
    private val currentAccountReader: CurrentAccountReader,
    private val quotaSnapshotDao: QuotaSnapshotDao,
) : NotificationWindowChoicesLoader {
    override suspend fun windowChoices(
        providerId: ProviderId?,
        localAccountId: LocalAccountId?,
    ): List<NotificationWindowChoice> {
        val resolvedProvider: ProviderId
        val resolvedLocal: LocalAccountId
        if (providerId != null && localAccountId != null) {
            resolvedProvider = providerId
            resolvedLocal = localAccountId
        } else {
            val selection = currentAccountReader.currentAccountSelection() ?: return emptyList()
            resolvedProvider = selection.providerId
            resolvedLocal = selection.localAccountId
        }
        val snapshot = quotaSnapshotDao.getLatestForAccount(
            providerId = resolvedProvider.value,
            localAccountId = resolvedLocal.value,
        )?.toDomain() ?: return emptyList()
        return snapshot.windows
            // A slot the provider never sent has nothing to notify about; hide it like Home does.
            .filterNot { it.availability == QuotaWindowAvailability.Missing }
            .map { NotificationWindowChoice(it.windowId, it.limitWindowSeconds) }
    }
}
