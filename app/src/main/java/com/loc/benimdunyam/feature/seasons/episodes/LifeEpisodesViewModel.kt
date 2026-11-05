
package com.loc.benimdunyam.feature.seasons.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import com.loc.benimdunyam.domain.usecase.DeleteEpisodeUseCase
import com.loc.benimdunyam.domain.usecase.DeleteLifeSeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.SaveEpisodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// --------------------------------------------------
// UI STATE
// --------------------------------------------------

data class LifeEpisodesUiState(
    val season: LifeSeason? = null,
    val episodes: List<LifeEpisode> = emptyList(),
    val allJournals: List<JournalEntry> = emptyList(),
    val activeEpisodeId: String? = null,
    val viewingMemoriesForEpisode: LifeEpisode? = null,
    val isLoading: Boolean = true,
    val searchQuery: String = ""
)

// --------------------------------------------------
// UI EVENTS
// --------------------------------------------------

sealed class LifeEpisodesEvent {

    object SeasonDeleted : LifeEpisodesEvent()

    object SeasonDeleteFailed : LifeEpisodesEvent()
}

// --------------------------------------------------
// VIEWMODEL
// --------------------------------------------------

@HiltViewModel
class LifeEpisodesViewModel @Inject constructor(

    savedStateHandle: SavedStateHandle,

    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeJournalsUseCase: ObserveJournalsUseCase,

    private val saveEpisodeUseCase: SaveEpisodeUseCase,
    private val deleteEpisodeUseCase: DeleteEpisodeUseCase,
    private val deleteLifeSeasonUseCase: DeleteLifeSeasonUseCase,

    private val lifeSeasonRepository: LifeSeasonRepository

) : ViewModel() {

    private val seasonId: String =
        checkNotNull(savedStateHandle["seasonId"])

    // --------------------------------------------------
    // SEARCH
    // --------------------------------------------------

    private val _searchQuery = MutableStateFlow("")

    // --------------------------------------------------
    // EPISODES
    // --------------------------------------------------

    private val _episodes =
        observeEpisodesBySeasonUseCase(seasonId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --------------------------------------------------
    // SEASON
    // --------------------------------------------------

    private val _season =
        observeLifeSeasonsUseCase()
            .map { seasons ->
                seasons.find { it.id == seasonId }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    // --------------------------------------------------
    // JOURNALS
    // --------------------------------------------------

    private val _journals =
        observeJournalsUseCase().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --------------------------------------------------
    // VIEWING MEMORIES
    // --------------------------------------------------

    private val _viewingMemoriesForEpisode =
        MutableStateFlow<LifeEpisode?>(null)

    // --------------------------------------------------
    // EVENTS
    // --------------------------------------------------

    private val _events =
        MutableSharedFlow<LifeEpisodesEvent>()

    val events: SharedFlow<LifeEpisodesEvent> =
        _events.asSharedFlow()

    // --------------------------------------------------
    // UI STATE
    // --------------------------------------------------

    val uiState: StateFlow<LifeEpisodesUiState> =

        combine(
            _searchQuery,
            _episodes,
            _season
        ) { query, episodes, season ->

            Triple(
                query,
                episodes,
                season
            )

        }.combine(

            combine(
                _journals,
                lifeSeasonRepository.activeEpisodeId,
                _viewingMemoriesForEpisode
            ) { journals, activeId, viewing ->

                Triple(
                    journals,
                    activeId,
                    viewing
                )
            }

        ) { first, second ->

            val (query, episodes, season) = first

            val (
                journals,
                activeEpisodeId,
                viewingEpisode
            ) = second

            // Arama filtresi
            val filteredEpisodes = if (query.isBlank()) {

                episodes

            } else {

                episodes.filter { episode ->

                    episode.title.contains(
                        query,
                        ignoreCase = true
                    ) || episode.description.contains(
                        query,
                        ignoreCase = true
                    )
                }
            }

            LifeEpisodesUiState(
                season = season,
                episodes = filteredEpisodes,
                allJournals = journals,
                activeEpisodeId = activeEpisodeId,
                viewingMemoriesForEpisode = viewingEpisode,
                isLoading = false,
                searchQuery = query
            )

        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LifeEpisodesUiState(
                isLoading = true
            )
        )

    // --------------------------------------------------
    // SEARCH QUERY
    // --------------------------------------------------

    fun onSearchQueryChanged(query: String) {

        _searchQuery.value = query
    }

    // --------------------------------------------------
    // SHOW EPISODE MEMORIES
    // --------------------------------------------------

    fun showMemoriesForEpisode(episode: LifeEpisode?) {

        _viewingMemoriesForEpisode.value = episode
    }

    // --------------------------------------------------
    // SELECT SEASON AND EPISODE
    // --------------------------------------------------

    fun setSelectedSeasonAndEpisode(episodeId: String) {

        lifeSeasonRepository.setActiveSeasonAndEpisode(
            seasonId,
            episodeId
        )
    }

    // --------------------------------------------------
    // SET ACTIVE EPISODE
    // --------------------------------------------------

    fun setActiveEpisode(episodeId: String) {

        lifeSeasonRepository.setActiveSeasonAndEpisode(
            seasonId,
            episodeId
        )
    }

    // --------------------------------------------------
    // CREATE EPISODE
    // --------------------------------------------------

    fun createEpisode(
        title: String,
        description: String,
        orderIndex: Int,
        startDate: Long,
        endDate: Long?
    ) {

        viewModelScope.launch {

            val newEpisodeId = UUID.randomUUID().toString()

            val newEpisode = LifeEpisode(

                id = newEpisodeId,
                seasonId = seasonId,
                title = title,
                description = description,
                orderIndex = orderIndex,
                startDate = startDate,
                endDate = endDate,
                createdAt = System.currentTimeMillis()
            )

            // 1. Bölümü kaydet
            saveEpisodeUseCase(newEpisode)

            // 2. Yeni bölümü aktif yap
            lifeSeasonRepository.setActiveSeasonAndEpisode(
                seasonId,
                newEpisodeId
            )
        }
    }

    // --------------------------------------------------
    // DELETE EPISODE
    // --------------------------------------------------

    fun deleteEpisode(episode: LifeEpisode) {

        viewModelScope.launch {

            deleteEpisodeUseCase(episode)
        }
    }

    // --------------------------------------------------
    // DELETE LIFE SEASON
    // --------------------------------------------------

    fun deleteSeason(season: LifeSeason) {

        viewModelScope.launch {

            try {

                // Yaşam dönemini sil
                deleteLifeSeasonUseCase(season)

                // Silme başarılı
                _events.emit(
                    LifeEpisodesEvent.SeasonDeleted
                )

            } catch (exception: Exception) {

                if (exception is CancellationException) {
                    throw exception
                }

                // Silme başarısız
                _events.emit(
                    LifeEpisodesEvent.SeasonDeleteFailed
                )
            }
        }
    }
}
