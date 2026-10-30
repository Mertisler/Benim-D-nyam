package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.repository.LifeEpisodeRepository
import javax.inject.Inject

class DeleteEpisodeUseCase @Inject constructor(
    private val repository: LifeEpisodeRepository
) {
    suspend operator fun invoke(episode: LifeEpisode) {
        repository.deleteEpisode(episode)
    }
}