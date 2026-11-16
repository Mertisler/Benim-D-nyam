package com.loc.benimdunyam.data.sync.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Senkronizasyon iş kayıtları için ayrı Room veritabanı.
 *
 * Böylece ana AppDatabase'in version=7 şemasına migration eklemiyoruz.
 * Bu dosya yalnızca planlama/iş durumunu tutar; günlük verisi burada yoktur.
 */
@Database(entities = [SyncTaskEntity::class], version = 1, exportSchema = false)
abstract class SyncOutboxDatabase : RoomDatabase() {
    abstract fun taskDao(): SyncTaskDao
}
