package com.loc.benimdunyam.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.LifeEpisodeDao
import com.loc.benimdunyam.data.local.dao.LifeSeasonDao
import com.loc.benimdunyam.data.local.dao.PeopleNetworkDao
import com.loc.benimdunyam.data.local.dao.PersonConnectionDao
import com.loc.benimdunyam.data.local.dao.PersonSpecialMemoryDao
import com.loc.benimdunyam.data.local.dao.PersonDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity

import com.loc.benimdunyam.data.local.dao.BackupRestoreDao

@Database(
    entities = [
        JournalEntryEntity::class,
        JournalSyncEventEntity::class,
        PersonEntity::class,
        JournalPersonMentionEntity::class,
        LifeSeasonEntity::class,
        LifeEpisodeEntity::class,
        PersonSpecialMemoryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun journalSyncEventDao(): JournalSyncEventDao
    abstract fun journalDao(): JournalDao
    abstract fun personDao(): PersonDao
    abstract fun journalPersonMentionDao(): JournalPersonMentionDao
    abstract fun lifeSeasonDao(): LifeSeasonDao
    abstract fun lifeEpisodeDao(): LifeEpisodeDao
    abstract fun personSpecialMemoryDao(): PersonSpecialMemoryDao
    abstract fun personConnectionDao(): PersonConnectionDao
    abstract fun peopleNetworkDao(): PeopleNetworkDao
    abstract fun backupRestoreDao(): BackupRestoreDao

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


        /**
         * Version 6 -> 7: new, empty table. All existing people/journals are kept.
         * FKs also remove the marker if its person or person-journal link is deleted.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `person_special_memories` (
                        `personId` TEXT NOT NULL,
                        `journalId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`personId`, `journalId`),
                        FOREIGN KEY(`personId`) REFERENCES `people`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`journalId`, `personId`)
                            REFERENCES `journal_person_mentions`(`journalId`, `personId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                database.execSQL(
                    """CREATE INDEX IF NOT EXISTS
                        `index_person_special_memories_journalId_personId`
                        ON `person_special_memories` (`journalId`, `personId`)
                    """.trimIndent()
                )
            }
        }

        /**
         * Versiyon 7 -> 8:
         * Yalnızca yeni journal_sync_events tablosu oluşturulur.
         * Mevcut günlükler, kişi ilişkileri, fotoğraflar ve yedek biçimi
         * değiştirilmez. Eski kayıtlar otomatik olarak bulut kuyruğuna ALINMAZ.
         * Başlangıç aktarımı sonraki aşamada açık kullanıcı onayıyla yapılır.
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `journal_sync_events` (
                        `journalId` TEXT NOT NULL,
                        `ownerUid` TEXT NOT NULL,
                        `changeType` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `changeVersion` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`journalId`)
                    )""".trimIndent()
                )
                database.execSQL(
                    """CREATE INDEX IF NOT EXISTS
                        `index_journal_sync_events_ownerUid_state`
                        ON `journal_sync_events` (`ownerUid`, `state`)
                    """.trimIndent()
                )
            }
        }
    }
}
