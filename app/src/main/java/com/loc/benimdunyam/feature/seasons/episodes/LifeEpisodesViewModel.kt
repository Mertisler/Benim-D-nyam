package com.loc.benimdunyam.feature.seasons.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import com.loc.benimdunyam.domain.usecase.DeleteLifeSeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.backupwrite.LockedEpisodeMutationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// UI State, ViewModel değildir. @HiltViewModel burada kullanılmaz.
data class LifeEpisodesUiState(
    val season: LifeSeason? = null,
    val episodes: List<LifeEpisode> = emptyList(),
    val allJournals: List<JournalEntry> = emptyList(),
    val activeEpisodeId: String? = null,
    val viewingMemoriesForEpisode: LifeEpisode? = null,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

sealed class LifeEpisodesEvent {
    object SeasonDeleted : LifeEpisodesEvent()
    object SeasonDeleteFailed : LifeEpisodesEvent()
}

private data class EpisodesObservedData(
    val season: LifeSeason?,
    val episodes: List<LifeEpisode>,
    val journals: List<JournalEntry>,
    val activeEpisodeId: String?
)

@HiltViewModel
class LifeEpisodesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeJournalsUseCase: ObserveJournalsUseCase,
    private val episodeMutations: LockedEpisodeMutationsUseCase,
    private val deleteLifeSeasonUseCase: DeleteLifeSeasonUseCase,
    private val writeCoordinator: BackupWriteCoordinator,
    lifeSeasonRepository: LifeSeasonRepository
) : ViewModel() {

    private val seasonId: String = checkNotNull(savedStateHandle["seasonId"])

    private val searchQuery = MutableStateFlow("")
    private val viewingMemoriesForEpisode = MutableStateFlow<LifeEpisode?>(null)
    private val isSaving = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<LifeEpisodesEvent>()
    val events: SharedFlow<LifeEpisodesEvent> = _events.asSharedFlow()

    private val observedData = combine(
        observeLifeSeasonsUseCase(),
        observeEpisodesBySeasonUseCase(seasonId),
        observeJournalsUseCase(),
        lifeSeasonRepository.activeEpisodeId
    ) { seasons, episodes, journals, activeId ->
        val sortedEpisodes = episodes.sortedWith(
            compareBy<LifeEpisode> { it.orderIndex }
                .thenBy { it.createdAt }
        )
        EpisodesObservedData(
            season = seasons.firstOrNull { it.id == seasonId },
            episodes = sortedEpisodes,
            journals = journals,
            activeEpisodeId = activeId?.takeIf { id ->
                sortedEpisodes.any { it.id == id }
            }
        )
    }

    val uiState: StateFlow<LifeEpisodesUiState> = combine(
        observedData,
        searchQuery,
        viewingMemoriesForEpisode,
        isSaving,
        errorMessage
    ) { observed, query, viewing, saving, error ->
        val trimmedQuery = query.trim()
        val visibleEpisodes = if (trimmedQuery.isBlank()) {
            observed.episodes
        } else {
            observed.episodes.filter { episode ->
                episode.title.contains(trimmedQuery, ignoreCase = true) ||
                        episode.description.contains(trimmedQuery, ignoreCase = true)
            }
        }

        LifeEpisodesUiState(
            season = observed.season,
            episodes = visibleEpisodes,
            allJournals = observed.journals,
            activeEpisodeId = observed.activeEpisodeId,
            viewingMemoriesForEpisode = viewing,
            isLoading = false,
            searchQuery = query,
            isSaving = saving,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LifeEpisodesUiState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun showMemoriesForEpisode(episode: LifeEpisode?) {
        viewingMemoriesForEpisode.value = episode
    }

    fun dismissError() {
        errorMessage.value = null
    }

    fun setSelectedSeasonAndEpisode(episodeId: String) {
        viewModelScope.launch {
            try {
                episodeMutations.select(seasonId, episodeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value =
                    e.localizedMessage ?: "Bölüm seçilemedi."
            }
        }
    }

    fun setActiveEpisode(episodeId: String) {
        setSelectedSeasonAndEpisode(episodeId)
    }

    fun createEpisode(
        title: String,
        description: String,
        orderIndex: Int,
        startDate: Long,
        endDate: Long?
    ) {
        if (isSaving.value) return

        val cleanedTitle = title.trim()
        if (cleanedTitle.isEmpty()) {
            errorMessage.value = "Bölüm başlığı boş olamaz."
            return
        }
        if (endDate != null && endDate < startDate) {
            errorMessage.value = "Bitiş tarihi başlangıçtan önce olamaz."
            return
        }

        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            try {
                val episode = LifeEpisode(
                    id = UUID.randomUUID().toString(),
                    seasonId = seasonId,
                    title = cleanedTitle,
                    description = description.trim(),
                    orderIndex = orderIndex,
                    startDate = startDate,
                    endDate = endDate,
                    createdAt = System.currentTimeMillis()
                )
                episodeMutations.createAndSelect(episode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value =
                    e.localizedMessage ?: "Bölüm oluşturulamadı."
            } finally {
                isSaving.value = false
            }
        }
    }

    fun deleteEpisode(episode: LifeEpisode) {
        if (isSaving.value) return
        if (episode.seasonId != seasonId) return

        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            try {
                episodeMutations.delete(episode)
                if (viewingMemoriesForEpisode.value?.id == episode.id) {
                    viewingMemoriesForEpisode.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value =
                    e.localizedMessage ?: "Bölüm silinemedi."
            } finally {
                isSaving.value = false
            }
        }
    }

    // Ekran hâlâ bu metodu çağırıyor olabilir diye korunuyor.
    // Sezon silme özelliği UI'dan çıkarıldıysa kullanılmamalıdır.
    fun deleteSeason(season: LifeSeason) {
        if (isSaving.value) return
        if (season.id != seasonId) return

        viewModelScope.launch {
            isSaving.value = true
            errorMessage.value = null
            try {
                writeCoordinator.withRegularWrite {
                    deleteLifeSeasonUseCase(season)
                }
                _events.emit(LifeEpisodesEvent.SeasonDeleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value =
                    e.localizedMessage ?: "Sezon silinemedi."
                _events.emit(LifeEpisodesEvent.SeasonDeleteFailed)
            } finally {
                isSaving.value = false
            }
        }
    }
}
