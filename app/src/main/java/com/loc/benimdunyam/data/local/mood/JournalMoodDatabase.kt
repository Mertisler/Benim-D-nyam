package com.loc.benimdunyam.data.local.mood

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Anı kimliğine göre saklanan ruh hali. Ana Room veritabanını değiştirmez. */
@Entity(tableName = "journal_mood_records")
data class JournalMoodRecord(
    @PrimaryKey val journalId: String,
    val moodName: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface JournalMoodDao {
    @Query("SELECT * FROM journal_mood_records")
    fun observeAll(): Flow<List<JournalMoodRecord>>

    @Query("SELECT COUNT(*) FROM journal_mood_records")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertForCloudRestore(records: List<JournalMoodRecord>)

    @Query("DELETE FROM journal_mood_records WHERE journalId = :journalId AND moodName = :name AND updatedAt = :updatedAt")
    suspend fun deleteIfStillStaged(journalId: String, name: String, updatedAt: Long)

    @Query("SELECT moodName FROM journal_mood_records WHERE journalId = :journalId LIMIT 1")
    suspend fun getName(journalId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(record: JournalMoodRecord)

    @Query("DELETE FROM journal_mood_records WHERE journalId = :journalId")
    suspend fun delete(journalId: String)
}

@Database(entities = [JournalMoodRecord::class], version = 1, exportSchema = false)
abstract class JournalMoodDatabase : RoomDatabase() {
    abstract fun moodDao(): JournalMoodDao
}
