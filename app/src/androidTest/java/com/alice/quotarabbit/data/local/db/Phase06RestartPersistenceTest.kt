package com.alice.quotarabbit.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.alice.quotarabbit.domain.model.LocalAccountId
import com.alice.quotarabbit.domain.model.ProviderId
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.data.repository.toEntity
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Invoke aSeedSyntheticMetadata and bVerifyAfterNewInstrumentationProcess in separate
 * adb instrumentation launches. Both use only a dedicated, non-production fixture database.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase06RestartPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun open() = Room.databaseBuilder(context, QuotaRabbitDatabase::class.java,
        "phase06-separate-process-synthetic.db").addMigrations(MIGRATION_1_2).build()

    @Test fun aSeedSyntheticMetadata() = runBlocking {
        val db = open()
        try {
            val account = ProviderAccount.createNew(
                localAccountId = LocalAccountId("phase06-synthetic"),
                providerId = ProviderId("codex"),
                providerAccountId = null,
                displayName = "Synthetic",
                now = Instant.parse("2026-10-10T00:00:00Z"),
            )
            val dao = db.providerAccountDao()
            dao.upsert(account.toEntity())
            assertEquals(1, dao.updateAccountPresentation(
                "codex", "phase06-synthetic", "After restart", "A",
                "violet", "2027-02-28", 3000L))
        } finally { db.close() }
    }

    @Test fun bVerifyAfterNewInstrumentationProcess() = runBlocking {
        val db = open()
        try {
            val saved = requireNotNull(db.providerAccountDao().getById("phase06-synthetic"))
            assertEquals("After restart", saved.displayName)
            assertEquals("violet", saved.accentColorId)
            assertEquals("2027-02-28", saved.manualRenewalDate)
            assertEquals("phase06-synthetic", saved.localAccountId)
        } finally { db.close() }
    }
}
