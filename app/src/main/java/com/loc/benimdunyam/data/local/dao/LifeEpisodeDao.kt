package com.loc.benimdunyam.data.local.dao

import androidx.room.*
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeEpisodeDao {

    @Query("SELECT * FROM life_episodes WHERE seasonId = :seasonId ORDER BY orderIndex ASC")
    fun observeEpisodesBySeason(seasonId: String): Flow<List<LifeEpisodeEntity>>

    @Query("SELECT * FROM life_episodes WHERE seasonId = :seasonId ORDER BY orderIndex ASC")
    suspend fun getEpisodesBySeason(seasonId: String): List<LifeEpisodeEntity>

    @Query("SELECT * FROM life_episodes WHERE id = :episodeId")
    suspend fun getEpisodeById(episodeId: String): LifeEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: LifeEpisodeEntity)

    @Delete
    suspend fun deleteEpisode(episode: LifeEpisodeEntity)
}