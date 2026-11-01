package com.loc.benimdunyam.data.repository

import com.loc.benimdunyam.data.local.dao.LifeSeasonDao
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LifeSeasonRepositoryImpl @Inject constructor(
    private val seasonDao: LifeSeasonDao
) : LifeSeasonRepository {

    private val _activeSeasonId = MutableStateFlow<String?>(null)
    override val activeSeasonId: StateFlow<String?> = _activeSeasonId.asStateFlow()

    // Aktif bölüm artık reaktif bir StateFlow
    private val _activeEpisodeId = MutableStateFlow<String?>(null)
    override val activeEpisodeId: StateFlow<String?> = _activeEpisodeId.asStateFlow()

    override fun setActiveSeasonAndEpisode(seasonId: String?, episodeId: String?) {
        _activeSeasonId.value = seasonId
        _activeEpisodeId.value = episodeId
    }

    override fun observeAllSeasons(): Flow<List<LifeSeason>> {
        return seasonDao.observeAllSeasons().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSeasonById(seasonId: String): LifeSeason? {
        return seasonDao.getSeasonById(seasonId)?.toDomain()
    }

    override suspend fun saveSeason(season: LifeSeason) {
        seasonDao.insertSeason(LifeSeasonEntity.fromDomain(season))
    }

    override suspend fun deleteSeason(season: LifeSeason) {
        seasonDao.deleteSeason(LifeSeasonEntity.fromDomain(season))
    }
}