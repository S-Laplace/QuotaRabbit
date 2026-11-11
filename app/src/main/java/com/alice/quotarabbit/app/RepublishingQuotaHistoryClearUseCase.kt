package com.alice.quotarabbit.app

import com.alice.quotarabbit.domain.settings.QuotaHistoryClearResult
import com.alice.quotarabbit.domain.settings.QuotaHistoryClearUseCase
import com.alice.quotarabbit.refresh.CurrentQuotaStatePublisher
import com.alice.quotarabbit.ui.home.HomeCurrentQuotaStateLoader
import kotlinx.coroutines.CancellationException

internal class RepublishingQuotaHistoryClearUseCase(
    private val delegate: QuotaHistoryClearUseCase,
    private val currentQuotaStateLoader: HomeCurrentQuotaStateLoader,
    private val currentQuotaStatePublisher: CurrentQuotaStatePublisher,
) : QuotaHistoryClearUseCase {
    override suspend fun clearCurrentAccountHistory(): QuotaHistoryClearResult =
        clearAndRepublish { delegate.clearCurrentAccountHistory() }

    override suspend fun clearAllHistory(): QuotaHistoryClearResult =
        clearAndRepublish { delegate.clearAllHistory() }

    private suspend fun clearAndRepublish(
        clear: suspend () -> QuotaHistoryClearResult,
    ): QuotaHistoryClearResult {
        val result = clear()
        try {
            currentQuotaStatePublisher.publish(currentQuotaStateLoader.loadCurrentState())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Data deletion is durable; presentation refresh can recover on the next state update.
        }
        return result
    }
}
