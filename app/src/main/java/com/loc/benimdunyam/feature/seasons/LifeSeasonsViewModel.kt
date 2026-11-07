package com.loc.benimdunyam.feature.seasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import com.loc.benimdunyam.domain.usecase.DeleteLifeSeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.SaveLifeSeasonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LifeSeasonSummary(
    val season: LifeSeason,
    val journalCount: Int,
    val totalWordCount: Int,
    val topMentionedPeople: List<Person>
)

data class LifeSeasonsUiState(
    val seasonSummaries: List<LifeSeasonSummary> = emptyList(),
    val activeSeasonId: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LifeSeasonsViewModel @Inject constructor(
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeJournalsUseCase: ObserveJournalsUseCase,
    private val saveLifeSeasonUseCase: SaveLifeSeasonUseCase,
    private val deleteLifeSeasonUseCase: DeleteLifeSeasonUseCase,
    private val lifeSeasonRepository: LifeSeasonRepository,
    private val writeCoordinator: BackupWriteCoordinator
) : ViewModel() {

    private val isSaving = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        observeLifeSeasonsUseCase(),
        observeJournalsUseCase(),
        lifeSeasonRepository.activeSeasonId
    ) { seasons, journals, activeSeasonId ->

        val summaries = seasons
            .sortedByDescending { it.createdAt }
            .map { season ->

                val seasonJournals = journals.filter {
                    it.seasonId == season.id
                }

                val peopleCounts = mutableMapOf<Person, Int>()

                seasonJournals.forEach { journal ->
                    journal.mentionedPeople
                        .distinctBy { it.id }
                        .forEach { person ->
                            peopleCounts[person] =
                                (peopleCounts[person] ?: 0) + 1
                        }
                }

                val words = seasonJournals.sumOf { journal ->
                    journal.content.trim()
                        .split(Regex("\\s+"))
                        .count { it.isNotBlank() }
                }

                LifeSeasonSummary(
                    season = season,
                    journalCount = seasonJournals.size,
                    totalWordCount = words,
                    topMentionedPeople = peopleCounts.entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .map { it.key }
                )
            }

        LifeSeasonsUiState(
            seasonSummaries = summaries,
            activeSeasonId = activeSeasonId,
            isLoading = false
        )
    }

    val uiState: StateFlow<LifeSeasonsUiState> = combine(
        dataFlow,
        isSaving,
        errorMessage
    ) { data, saving, error ->

        data.copy(
            isSaving = saving,
            errorMessage = error
        )

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LifeSeasonsUiState()
    )

    fun selectSeason(seasonId: String) {
        viewModelScope.launch {
            try {
                writeCoordinator.withRegularWrite {
                    lifeSeasonRepository.setActiveSeasonAndEpisode(
                        seasonId,
                        null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value =
                    e.localizedMessage ?: "Sezon seçilemedi."
            }
        }
    }

    fun createSeason(
        title: String,
        description: String,
        startDate: Long,
        endDate: Long?,
        colorHex: String,
        coverImagePath: String?
    ) {
        if (isSaving.value) return

        val cleanedTitle = title.trim()

        if (cleanedTitle.isEmpty()) {
            errorMessage.value = "Sezon başlığı boş olamaz."
            return
        }

        if (endDate != null && endDate < startDate) {
            errorMessage.value =
                "Bitiş tarihi başlangıç tarihinden önce olamaz."
            return
        }

        isSaving.value = true
        errorMessage.value = null

        viewModelScope.launch {
            try {
                val season = LifeSeason(
                    id = UUID.randomUUID().toString(),
                    title = cleanedTitle,
                    description = description.trim(),
                    startDate = startDate,
                    endDate = endDate,
                    colorHex = colorHex,
                    coverImagePath = coverImagePath,
                    createdAt = System.currentTimeMillis()
                )

                writeCoordinator.withRegularWrite {
                    saveLifeSeasonUseCase(season)

                    lifeSeasonRepository.setActiveSeasonAndEpisode(
                        season.id,
                        null
                    )
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value =
                    e.localizedMessage ?: "Sezon oluşturulamadı."
            } finally {
                isSaving.value = false
            }
        }
    }

    // Önceki çağrılarla uyumluluk için korunuyor.
    // Sezon listesinde silme butonu gerekmiyor.
    fun deleteSeason(season: LifeSeason) {
        if (isSaving.value) return

        isSaving.value = true
        errorMessage.value = null

        viewModelScope.launch {
            try {
                writeCoordinator.withRegularWrite {
                    deleteLifeSeasonUseCase(season)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value =
                    e.localizedMessage ?: "Sezon silinemedi."
            } finally {
                isSaving.value = false
            }
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }
}
