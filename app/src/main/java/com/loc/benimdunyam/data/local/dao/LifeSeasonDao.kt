package com.loc.benimdunyam.data.local.dao

import androidx.room.*
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeSeasonDao {
    @Query("SELECT * FROM life_seasons ORDER BY startDate ASC")
    fun observeAllSeasons(): Flow<List<LifeSeasonEntity>>

    @Query("SELECT * FROM life_seasons")
    suspend fun getAllSeasonEntities(): List<LifeSeasonEntity>

    @Query("SELECT * FROM life_seasons WHERE id = :seasonId")
    suspend fun getSeasonById(seasonId: String): LifeSeasonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeason(season: LifeSeasonEntity)

    @Delete
    suspend fun deleteSeason(season: LifeSeasonEntity)

    // Faz 2G.1: Eski dönem kapağı varsa üzerine yazmak YASAKTIR.
    @Query("UPDATE life_seasons SET coverImagePath = :path WHERE id = :id AND (coverImagePath IS NULL OR coverImagePath = '')")
    suspend fun setCoverImageIfEmpty(id: String, path: String): Int
}