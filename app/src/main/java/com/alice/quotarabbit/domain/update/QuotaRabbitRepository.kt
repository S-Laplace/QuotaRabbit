package com.alice.quotarabbit.domain.update

/** Public source repository and GitHub Releases endpoint for application updates. */
object QuotaRabbitRepository {
    private const val OWNER = "S-Laplace"
    private const val REPOSITORY = "QuotaRabbit"

    private val validOwner = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")
    private val validRepository = Regex("[A-Za-z0-9_.-]+")

    val isConfigured: Boolean
        get() = validOwner.matches(OWNER) && validRepository.matches(REPOSITORY)

    val repositoryUrl: String?
        get() = if (isConfigured) "https://github.com/$OWNER/$REPOSITORY" else null

    val latestReleaseApiUrl: String?
        get() = if (isConfigured) "https://api.github.com/repos/$OWNER/$REPOSITORY/releases/latest" else null

    val releasesPageUrl: String?
        get() = repositoryUrl?.plus("/releases")
}
