package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import javax.inject.Inject

class DeleteLifeSeasonUseCase @Inject constructor(
    private val repository: LifeSeasonRepository
) {
    suspend operator fun invoke(season: LifeSeason) {
        repository.deleteSeason(season)
    }
}