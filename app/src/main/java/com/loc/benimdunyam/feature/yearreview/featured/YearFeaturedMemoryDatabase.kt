package com.loc.benimdunyam.feature.yearreview.featured

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Mevcut AppDatabase'den bağımsızdır: uygulamanın ana Room sürümünü/migration'larını değiştirmez.
 * journalId, ana günlük tablosuna mantıksal referanstır. Farklı DB'ler arası FK olamaz;
 * eski seçimler YearFeaturedMemoryRepository.reconcile ile temizlenir.
 */
@Entity(
    tableName = "year_featured_memories",
    indices = [Index(value = ["year", "position"])]
)
data class YearFeaturedMemoryEntity(
    @PrimaryKey val journalId: String,
    val year: Int,
    val position: Int,
    val addedAt: Long
)

@Dao
interface YearFeaturedMemoryDao {
    @Query("SELECT * FROM year_featured_memories ORDER BY year DESC, position ASC, addedAt ASC")
    fun observeAll(): Flow<List<YearFeaturedMemoryEntity>>

    @Query("SELECT * FROM year_featured_memories ORDER BY year DESC, position ASC, addedAt ASC")
    suspend fun getAll(): List<YearFeaturedMemoryEntity>

    @Query("SELECT * FROM year_featured_memories WHERE year = :year ORDER BY position ASC, addedAt ASC, journalId ASC")
    suspend fun getForYear(year: Int): List<YearFeaturedMemoryEntity>

    @Query("SELECT * FROM year_featured_memories WHERE journalId = :journalId LIMIT 1")
    suspend fun getById(journalId: String): YearFeaturedMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: YearFeaturedMemoryEntity)

    @Query("DELETE FROM year_featured_memories WHERE journalId = :journalId")
    suspend fun delete(journalId: String)

    @Query("UPDATE year_featured_memories SET year = :year WHERE journalId = :journalId")
    suspend fun updateYear(journalId: String, year: Int)

    @Query("UPDATE year_featured_memories SET position = :position WHERE journalId = :journalId")
    suspend fun updatePosition(journalId: String, position: Int)
}

@Database(
    entities = [YearFeaturedMemoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class YearFeaturedMemoryDatabase : RoomDatabase() {
    abstract fun dao(): YearFeaturedMemoryDao
}
