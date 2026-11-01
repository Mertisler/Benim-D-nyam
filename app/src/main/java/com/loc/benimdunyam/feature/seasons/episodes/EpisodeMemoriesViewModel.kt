package com.loc.benimdunyam.feature.seasons.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class EpisodeMemoriesUiState(
    val episode: LifeEpisode? = null,
    val journals: List<JournalEntry> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class EpisodeMemoriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeJournalsUseCase: ObserveJournalsUseCase,
    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase
) : ViewModel() {

    private val seasonId: String = checkNotNull(savedStateHandle["seasonId"])
    private val episodeId: String = checkNotNull(savedStateHandle["episodeId"])

    val uiState: StateFlow<EpisodeMemoriesUiState> = combine(
        observeEpisodesBySeasonUseCase(seasonId),
        observeJournalsUseCase()
    ) { episodes, journals ->
        val episode = episodes.find { it.id == episodeId }
        val episodeJournals = journals.filter { it.episodeId == episodeId }
        EpisodeMemoriesUiState(
            episode = episode,
            journals = episodeJournals,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EpisodeMemoriesUiState(isLoading = true)
    )
}