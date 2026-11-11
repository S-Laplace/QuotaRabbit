package com.alice.quotarabbit.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaRabbitRepositoryTest {
    @Test fun `repository URLs target the public QuotaRabbit releases`() {
        assertTrue(QuotaRabbitRepository.isConfigured)
        assertEquals("https://github.com/S-Laplace/QuotaRabbit", QuotaRabbitRepository.repositoryUrl)
        assertEquals("https://api.github.com/repos/S-Laplace/QuotaRabbit/releases/latest", QuotaRabbitRepository.latestReleaseApiUrl)
        assertEquals("https://github.com/S-Laplace/QuotaRabbit/releases", QuotaRabbitRepository.releasesPageUrl)
    }
}
