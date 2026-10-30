package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import javax.inject.Inject

class SaveLifeSeasonUseCase @Inject constructor(
    private val repository: LifeSeasonRepository
) {
    suspend operator fun invoke(season: LifeSeason) {
        repository.saveSeason(season)
    }
}