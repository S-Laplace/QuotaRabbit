package com.alice.quotarabbit.ui.settings

import android.content.Intent
import android.net.Uri

internal object SettingsRepositoryLinkTarget {
    fun openIntent(): Intent? = com.alice.quotarabbit.domain.update.QuotaRabbitRepository.repositoryUrl
        ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
}
