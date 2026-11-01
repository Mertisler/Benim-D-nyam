package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.LifeSeason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LifeSeasonRepository {
    fun observeAllSeasons(): Flow<List<LifeSeason>>
    suspend fun getSeasonById(seasonId: String): LifeSeason?
    suspend fun saveSeason(season: LifeSeason)
    suspend fun deleteSeason(season: LifeSeason)
    val activeSeasonId: StateFlow<String?>
    val activeEpisodeId: StateFlow<String?>
    fun setActiveSeasonAndEpisode(seasonId: String?, episodeId: String? = null)
}