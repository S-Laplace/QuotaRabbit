package com.alice.quotarabbit.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Creates an isolated v1 database fixture; never opens or alters the user's QuotaRabbit DB. */
@RunWith(AndroidJUnit4::class)
class Phase06MigrationTest {
    @Test fun migrateV1AccountsAndHistoryThroughV3AndReopenBenefits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "phase06-synthetic-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val schema = listOf(
                "CREATE TABLE provider_accounts (local_account_id TEXT NOT NULL PRIMARY KEY, provider_id TEXT NOT NULL, provider_account_id TEXT, display_name TEXT NOT NULL, avatar_initial TEXT NOT NULL, avatar_color_key TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, last_successful_refresh_at INTEGER)",
                "CREATE UNIQUE INDEX index_provider_accounts_provider_id_provider_account_id ON provider_accounts(provider_id, provider_account_id)",
                "CREATE INDEX index_provider_accounts_provider_id_local_account_id ON provider_accounts(provider_id, local_account_id)",
                "CREATE TABLE quota_snapshots (snapshot_id TEXT NOT NULL PRIMARY KEY, provider_id TEXT NOT NULL, local_account_id TEXT NOT NULL, provider_account_id TEXT, fetched_at INTEGER NOT NULL, source TEXT NOT NULL, plan_type TEXT, windows_json TEXT NOT NULL, credits_json TEXT, response_digest TEXT)",
                "CREATE INDEX index_quota_snapshots_provider_id_local_account_id_fetched_at ON quota_snapshots(provider_id, local_account_id, fetched_at)",
                "CREATE TABLE refresh_attempts (attempt_id TEXT NOT NULL PRIMARY KEY, provider_id TEXT NOT NULL, local_account_id TEXT NOT NULL, trigger TEXT NOT NULL, started_at INTEGER NOT NULL, finished_at INTEGER, status TEXT NOT NULL, error_code TEXT, http_status INTEGER, retryable INTEGER, user_action_required INTEGER, diagnostics_digest TEXT)",
                "CREATE INDEX index_refresh_attempts_provider_id_local_account_id_started_at ON refresh_attempts(provider_id, local_account_id, started_at)",
                "CREATE TABLE alert_states (alert_state_id TEXT NOT NULL PRIMARY KEY, provider_id TEXT NOT NULL, local_account_id TEXT NOT NULL, window_id TEXT NOT NULL, threshold REAL NOT NULL, reset_at TEXT NOT NULL, last_notified_at TEXT NOT NULL)",
                "CREATE UNIQUE INDEX index_alert_states_provider_id_local_account_id_window_id_reset_at_threshold ON alert_states(provider_id, local_account_id, window_id, reset_at, threshold)",
            )
            schema.forEach { sqlite.execSQL(it) }
            sqlite.execSQL("INSERT INTO provider_accounts VALUES (?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>("local-1", "codex", "provider-1", "Work", "W", "local-1",
                    "Active", 1000L, 1000L, 2000L))
            sqlite.execSQL("INSERT INTO quota_snapshots VALUES (?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>("snap-1", "codex", "local-1", "provider-1", 2000L,
                    "ManualRefresh", "plus", "[]", null, "digest"))
            sqlite.version = 1
        } finally {
            sqlite.close()
        }

        fun open() = Room.databaseBuilder(context, QuotaRabbitDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        val upgraded = open()
        val account = upgraded.providerAccountDao().getById("local-1")
        assertNotNull(account)
        assertEquals("provider-1", account!!.providerAccountId)
        assertEquals("local-1", account.avatarColorKey)
        assertNull(account.accentColorId)
        assertNull(account.manualRenewalDate)
        val oldSnapshot = upgraded.quotaSnapshotDao()
            .getLatestForAccount("codex", "local-1")
        assertEquals("snap-1", oldSnapshot?.snapshotId)
        assertNull(oldSnapshot?.providerBenefitsJson)
        upgraded.quotaSnapshotDao().insert(oldSnapshot!!.copy(
            snapshotId = "snap-benefits",
            fetchedAt = 2500L,
            providerBenefitsJson = """{"bankedResetCount":2,"subscriptionRenewsAtMs":1800000000000}""",
        ))
        assertEquals(1, upgraded.providerAccountDao().updateAccountPresentation(
            "codex", "local-1", "Renamed", "R", "teal", "2026-03-01", 3000L))
        upgraded.close()

        val reopened = open()
        val saved = reopened.providerAccountDao().getById("local-1")!!
        assertEquals("Renamed", saved.displayName)
        assertEquals("teal", saved.accentColorId)
        assertEquals("2026-03-01", saved.manualRenewalDate)
        assertEquals("provider-1", saved.providerAccountId)
        val benefits = reopened.quotaSnapshotDao().getLatestForAccount("codex", "local-1")
        assertEquals("snap-benefits", benefits?.snapshotId)
        assertEquals("""{"bankedResetCount":2,"subscriptionRenewsAtMs":1800000000000}""",
            benefits?.providerBenefitsJson)
        reopened.close()
    }
}
