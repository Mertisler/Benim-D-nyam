package com.loc.benimdunyam.domain.usecase.backupwrite

import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import com.loc.benimdunyam.domain.usecase.DeleteEpisodeUseCase
import com.loc.benimdunyam.domain.usecase.SaveEpisodeUseCase
import javax.inject.Inject

/**
 * Var olan SaveEpisodeUseCase ve DeleteEpisodeUseCase is mantigini
 * degistirmeden, bolum yazmalarini ortak geri yukleme kilidine baglar.
 *
 * Bu sinifi kullanan tum bolum degisiklikleri geri yukleme bitene
 * kadar bekler. BackupWriteCoordinator ayni coroutine icindeki
 * ic ice cagrilari desteklemelidir.
 */
class LockedEpisodeMutationsUseCase @Inject constructor(
    private val saveEpisodeUseCase: SaveEpisodeUseCase,
    private val deleteEpisodeUseCase: DeleteEpisodeUseCase,
    private val lifeSeasonRepository: LifeSeasonRepository,
    private val writeCoordinator: BackupWriteCoordinator
) {
    suspend fun createAndSelect(episode: LifeEpisode) {
        writeCoordinator.withRegularWrite {
            saveEpisodeUseCase(episode)
            lifeSeasonRepository.setActiveSeasonAndEpisode(
                episode.seasonId,
                episode.id
            )
        }
    }

    suspend fun save(episode: LifeEpisode) {
        writeCoordinator.withRegularWrite {
            saveEpisodeUseCase(episode)
        }
    }

    suspend fun delete(episode: LifeEpisode) {
        writeCoordinator.withRegularWrite {
            deleteEpisodeUseCase(episode)
        }
    }

    suspend fun select(seasonId: String, episodeId: String) {
        writeCoordinator.withRegularWrite {
            lifeSeasonRepository.setActiveSeasonAndEpisode(
                seasonId,
                episodeId
            )
        }
    }
}
