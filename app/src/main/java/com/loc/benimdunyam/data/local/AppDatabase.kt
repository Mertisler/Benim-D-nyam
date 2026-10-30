package com.loc.benimdunyam.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 5,
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
    }
}