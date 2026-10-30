package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.repository.LifeEpisodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveEpisodesBySeasonUseCase @Inject constructor(
    private val repository: LifeEpisodeRepository
) {
    operator fun invoke(seasonId: String): Flow<List<LifeEpisode>> {
        return repository.observeEpisodesBySeason(seasonId)
    }
}