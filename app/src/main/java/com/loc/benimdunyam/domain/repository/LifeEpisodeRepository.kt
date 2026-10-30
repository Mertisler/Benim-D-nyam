package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.LifeEpisode
import kotlinx.coroutines.flow.Flow

interface LifeEpisodeRepository {
    fun observeEpisodesBySeason(seasonId: String): Flow<List<LifeEpisode>>
    suspend fun getEpisodeById(episodeId: String): LifeEpisode?
    suspend fun saveEpisode(episode: LifeEpisode)
    suspend fun deleteEpisode(episode: LifeEpisode)
}