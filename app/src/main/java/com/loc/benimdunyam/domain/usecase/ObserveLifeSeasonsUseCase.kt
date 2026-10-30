package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveLifeSeasonsUseCase @Inject constructor(
    private val repository: LifeSeasonRepository
) {
    operator fun invoke(): Flow<List<LifeSeason>> = repository.observeAllSeasons()
}