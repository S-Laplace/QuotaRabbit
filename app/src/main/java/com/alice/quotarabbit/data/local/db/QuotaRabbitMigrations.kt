package com.alice.quotarabbit.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add optional account presentation metadata without touching sessions or historical quotas. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE provider_accounts ADD COLUMN accent_color_id TEXT")
        db.execSQL("ALTER TABLE provider_accounts ADD COLUMN manual_renewal_date TEXT")
    }
}

/** Optional provider benefit projection, keeping every existing quota/history row untouched. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE quota_snapshots ADD COLUMN provider_benefits_json TEXT")
    }
}
