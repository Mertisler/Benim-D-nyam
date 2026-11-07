
package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loc.benimdunyam.data.local.entity.*

/**
 * Yalnızca tam veritabanı geri yüklemesinde kullanılır.
 *
 * Bu metotların tek bir Room transaction
 * içerisinde çağrılması gerekir.
 */
@Dao
interface BackupRestoreDao {

    // Önce bağımlı tablolar temizlenir.

    @Query("DELETE FROM person_special_memories")
    suspend fun clearSpecialMemories()

    @Query("DELETE FROM journal_person_mentions")
    suspend fun clearMentions()

    @Query("DELETE FROM journal_entries")
    suspend fun clearJournals()

    @Query("DELETE FROM life_episodes")
    suspend fun clearEpisodes()

    @Query("DELETE FROM life_seasons")
    suspend fun clearSeasons()

    @Query("DELETE FROM people")
    suspend fun clearPeople()

    // Önce ana tablolar, sonra ilişkiler eklenir.
    // ABORT beklenmedik anahtar çakışmalarını gizlemez.

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSeasons(
        seasons: List<LifeSeasonEntity>
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPeople(
        people: List<PersonEntity>
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEpisodes(
        episodes: List<LifeEpisodeEntity>
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournals(
        journals: List<JournalEntryEntity>
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMentions(
        mentions: List<JournalPersonMentionEntity>
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSpecialMemories(
        memories: List<PersonSpecialMemoryEntity>
    )
}
