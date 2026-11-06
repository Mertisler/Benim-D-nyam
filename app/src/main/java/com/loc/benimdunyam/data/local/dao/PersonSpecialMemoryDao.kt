package com.loc.benimdunyam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonSpecialMemoryDao {
    @Query("SELECT * FROM person_special_memories WHERE personId = :personId ORDER BY createdAt DESC")
    fun observeForPerson(personId: String): Flow<List<PersonSpecialMemoryEntity>>

    @Query("SELECT * FROM person_special_memories WHERE personId = :personId AND journalId = :journalId LIMIT 1")
    suspend fun get(personId: String, journalId: String): PersonSpecialMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PersonSpecialMemoryEntity)

    @Query("DELETE FROM person_special_memories WHERE personId = :personId AND journalId = :journalId")
    suspend fun delete(personId: String, journalId: String)
}
