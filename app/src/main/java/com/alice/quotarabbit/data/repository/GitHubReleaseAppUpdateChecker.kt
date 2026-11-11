package com.alice.quotarabbit.data.repository

import com.alice.quotarabbit.core.network.ProviderHttpClient
import com.alice.quotarabbit.domain.update.AppUpdateCheckResult
import com.alice.quotarabbit.domain.update.AppUpdateCheckUseCase
import com.alice.quotarabbit.domain.update.AppUpdateInfo
import com.alice.quotarabbit.domain.update.AppVersionComparator
import com.alice.quotarabbit.domain.update.QuotaRabbitRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GitHubReleaseAppUpdateChecker(
    private val httpClient: ProviderHttpClient,
    private val latestReleaseApiUrl: String? = QuotaRabbitRepository.latestReleaseApiUrl,
    private val releasesPageUrl: String? = QuotaRabbitRepository.releasesPageUrl,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AppUpdateCheckUseCase {
    override suspend fun checkForUpdate(currentVersionName: String): AppUpdateCheckResult {
        val endpoint = latestReleaseApiUrl?.takeIf { it.isNotBlank() }
            ?: return AppUpdateCheckResult.NoRelease
        val response = runCatching {
            httpClient.get(
                url = endpoint,
                headers = mapOf(
                    "Accept" to "application/vnd.github+json",
                    "User-Agent" to "QuotaRabbit-Android",
                ),
            )
        }.getOrElse {
            return AppUpdateCheckResult.Failure("github_release_request_failed")
        }

        if (response.statusCode == HTTP_NOT_FOUND) return AppUpdateCheckResult.NoRelease
        if (response.statusCode !in HTTP_SUCCESS_RANGE) {
            return AppUpdateCheckResult.Failure("github_release_http_${response.statusCode}")
        }

        val release = runCatching {
            json.decodeFromString(GitHubLatestReleaseDto.serializer(), response.body)
        }.getOrElse {
            return AppUpdateCheckResult.Failure("github_release_parse_failed")
        }
        val versionName = release.tagName?.takeIf { it.isNotBlank() }
            ?: release.name?.takeIf { it.isNotBlank() }
            ?: return AppUpdateCheckResult.NoRelease

        if (!AppVersionComparator.isRemoteVersionNewer(currentVersionName, versionName)) {
            return AppUpdateCheckResult.UpToDate
        }

        val apkAsset = release.assets.firstOrNull { asset ->
            asset.name.endsWith(APK_SUFFIX, ignoreCase = true) && asset.browserDownloadUrl.isNotBlank()
        } ?: return AppUpdateCheckResult.NoApkAsset

        return AppUpdateCheckResult.UpdateAvailable(
            AppUpdateInfo(
                versionName = versionName,
                releasePageUrl = release.htmlUrl?.takeIf { it.isNotBlank() } ?: releasesPageUrl.orEmpty(),
                apkDownloadUrl = apkAsset.browserDownloadUrl,
                apkFileName = apkAsset.name,
                releaseNotes = release.body?.takeIf { it.isNotBlank() },
            ),
        )
    }

    @Serializable
    private data class GitHubLatestReleaseDto(
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val assets: List<GitHubReleaseAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAssetDto(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )

    private companion object {
        const val HTTP_NOT_FOUND = 404
        val HTTP_SUCCESS_RANGE = 200..299
        const val APK_SUFFIX = ".apk"
    }
}
