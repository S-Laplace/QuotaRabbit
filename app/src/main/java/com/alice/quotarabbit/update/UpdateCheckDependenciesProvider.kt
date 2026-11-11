package com.alice.quotarabbit.update

import com.alice.quotarabbit.domain.update.AppUpdateCheckUseCase
import com.alice.quotarabbit.domain.update.AppUpdateNotifier
import com.alice.quotarabbit.domain.update.UpdatePreferenceStore

/** Implemented by the Application so [UpdateCheckWorker] can reach its dependencies. */
interface UpdateCheckDependenciesProvider {
    val appUpdateCheck: AppUpdateCheckUseCase
    val updatePreferenceStore: UpdatePreferenceStore
    val appUpdateNotifier: AppUpdateNotifier
    val currentVersionName: String
}
