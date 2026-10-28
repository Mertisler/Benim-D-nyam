package com.loc.benimdunyam.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.PersonDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity

@Database(
    entities = [
        JournalEntryEntity::class,
        PersonEntity::class,
        JournalPersonMentionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao
    abstract fun personDao(): PersonDao
    abstract fun journalPersonMentionDao(): JournalPersonMentionDao

    companion object {
        const val DATABASE_NAME = "benim_dunyam_db"
    }
}