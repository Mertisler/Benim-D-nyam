package com.loc.benimdunyam.data.repository

import com.loc.benimdunyam.data.local.dao.LifeEpisodeDao
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.repository.LifeEpisodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LifeEpisodeRepositoryImpl @Inject constructor(
    private val episodeDao: LifeEpisodeDao
) : LifeEpisodeRepository {

    override fun observeEpisodesBySeason(seasonId: String): Flow<List<LifeEpisode>> {
        return episodeDao.observeEpisodesBySeason(seasonId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEpisodeById(episodeId: String): LifeEpisode? {
        return episodeDao.getEpisodeById(episodeId)?.toDomain()
    }

    override suspend fun saveEpisode(episode: LifeEpisode) {
        episodeDao.insertEpisode(LifeEpisodeEntity.fromDomain(episode))
    }

    override suspend fun deleteEpisode(episode: LifeEpisode) {
        episodeDao.deleteEpisode(LifeEpisodeEntity.fromDomain(episode))
    }
}