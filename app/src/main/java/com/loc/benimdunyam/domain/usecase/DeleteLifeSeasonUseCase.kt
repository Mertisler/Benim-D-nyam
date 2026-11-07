package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import javax.inject.Inject

class DeleteLifeSeasonUseCase @Inject constructor(
    private val repository: LifeSeasonRepository,
    private val writeCoordinator: BackupWriteCoordinator
) {
    suspend operator fun invoke(season: LifeSeason) {
        writeCoordinator.withRegularWrite {
            repository.deleteSeason(season)
        }
    }
}
