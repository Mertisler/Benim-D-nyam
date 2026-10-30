package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.LifeSeason
import kotlinx.coroutines.flow.Flow

interface LifeSeasonRepository {
    fun observeAllSeasons(): Flow<List<LifeSeason>>
    suspend fun getSeasonById(seasonId: String): LifeSeason?
    suspend fun saveSeason(season: LifeSeason)
    suspend fun deleteSeason(season: LifeSeason)
}