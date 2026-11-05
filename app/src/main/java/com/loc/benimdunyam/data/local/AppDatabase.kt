package com.loc.benimdunyam.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.LifeEpisodeDao
import com.loc.benimdunyam.data.local.dao.LifeSeasonDao
import com.loc.benimdunyam.data.local.dao.PersonDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity

@Database(
    entities = [
        JournalEntryEntity::class,
        PersonEntity::class,
        JournalPersonMentionEntity::class,
        LifeSeasonEntity::class,
        LifeEpisodeEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao
    abstract fun personDao(): PersonDao
    abstract fun journalPersonMentionDao(): JournalPersonMentionDao
    abstract fun lifeSeasonDao(): LifeSeasonDao
    abstract fun lifeEpisodeDao(): LifeEpisodeDao

    companion object {
        const val DATABASE_NAME = "benim_dunyam_db"

        /**
         * Version 5 -> 6:
         * Adds optional app-private image paths to existing people rows.
         * Existing people are preserved and both new columns start as NULL.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE people ADD COLUMN profileImagePath TEXT"
                )
                database.execSQL(
                    "ALTER TABLE people ADD COLUMN coverImagePath TEXT"
                )
            }
        }
    }
}
