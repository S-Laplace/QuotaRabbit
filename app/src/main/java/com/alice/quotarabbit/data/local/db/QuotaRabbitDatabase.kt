package com.alice.quotarabbit.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.alice.quotarabbit.data.local.dao.AlertStateDao
import com.alice.quotarabbit.data.local.dao.ProviderAccountDao
import com.alice.quotarabbit.data.local.dao.QuotaSnapshotDao
import com.alice.quotarabbit.data.local.dao.RefreshAttemptDao
import com.alice.quotarabbit.data.local.entity.AlertStateEntity
import com.alice.quotarabbit.data.local.entity.ProviderAccountEntity
import com.alice.quotarabbit.data.local.entity.QuotaSnapshotEntity
import com.alice.quotarabbit.data.local.entity.RefreshAttemptEntity

const val QUOTARABBIT_DB_SCHEMA_VERSION = 3

@Database(
    entities = [
        ProviderAccountEntity::class,
        QuotaSnapshotEntity::class,
        RefreshAttemptEntity::class,
        AlertStateEntity::class,
    ],
    version = QUOTARABBIT_DB_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class QuotaRabbitDatabase : RoomDatabase() {
    abstract fun providerAccountDao(): ProviderAccountDao

    abstract fun quotaSnapshotDao(): QuotaSnapshotDao

    abstract fun refreshAttemptDao(): RefreshAttemptDao

    abstract fun alertStateDao(): AlertStateDao

    companion object {
        const val DATABASE_NAME = "quotarabbit.db"
    }
}
