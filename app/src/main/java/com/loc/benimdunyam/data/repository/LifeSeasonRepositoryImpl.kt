package com.loc.benimdunyam.data.repository

import com.loc.benimdunyam.data.local.dao.LifeSeasonDao
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LifeSeasonRepositoryImpl @Inject constructor(
    private val lifeSeasonDao: LifeSeasonDao
) : LifeSeasonRepository {

    override fun observeAllSeasons(): Flow<List<LifeSeason>> {
        return lifeSeasonDao.observeAllSeasons().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSeasonById(seasonId: String): LifeSeason? {
        return lifeSeasonDao.getSeasonById(seasonId)?.toDomain()
    }

    override suspend fun saveSeason(season: LifeSeason) {
        lifeSeasonDao.insertSeason(LifeSeasonEntity.fromDomain(season))
    }

    override suspend fun deleteSeason(season: LifeSeason) {
        lifeSeasonDao.deleteSeason(LifeSeasonEntity.fromDomain(season))
    }
}