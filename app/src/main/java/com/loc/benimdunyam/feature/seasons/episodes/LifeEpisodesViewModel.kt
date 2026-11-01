package com.loc.benimdunyam.feature.seasons.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import com.loc.benimdunyam.domain.usecase.DeleteEpisodeUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.SaveEpisodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class LifeEpisodesUiState(
    val season: LifeSeason? = null,
    val episodes: List<LifeEpisode> = emptyList(),
    val allJournals: List<JournalEntry> = emptyList(),
    val activeEpisodeId: String? = null,
    val viewingMemoriesForEpisode: LifeEpisode? = null, // Anıları gösterilecek bölüm
    val isLoading: Boolean = true,
    val searchQuery: String = ""
)

@HiltViewModel
class LifeEpisodesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeJournalsUseCase: ObserveJournalsUseCase, // Gözlemci eklendi
    private val saveEpisodeUseCase: SaveEpisodeUseCase,
    private val deleteEpisodeUseCase: DeleteEpisodeUseCase,
    private val lifeSeasonRepository: LifeSeasonRepository
) : ViewModel() {

    private val seasonId: String = checkNotNull(savedStateHandle["seasonId"])

    private val _searchQuery = MutableStateFlow("")

    private val _episodes = observeEpisodesBySeasonUseCase(seasonId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _season = observeLifeSeasonsUseCase().map { seasons ->
        seasons.find { it.id == seasonId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _journals = observeJournalsUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _viewingMemoriesForEpisode = MutableStateFlow<LifeEpisode?>(null)

    val uiState: StateFlow<LifeEpisodesUiState> = combine(
        _searchQuery,
        _episodes,
        _season
    ) { query, episodes, season ->
        Triple(query, episodes, season)
    }.combine(
        combine(_journals, lifeSeasonRepository.activeEpisodeId, _viewingMemoriesForEpisode) { j, activeId, viewing ->
            Triple(j, activeId, viewing)
        }
    ) { (query, episodes, season), (journals, activeEpisodeId, viewingEpisode) ->
        val filtered = if (query.isBlank()) {
            episodes
        } else {
            episodes.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
        LifeEpisodesUiState(
            season = season,
            episodes = filtered,
            allJournals = journals,
            activeEpisodeId = activeEpisodeId,
            viewingMemoriesForEpisode = viewingEpisode,
            isLoading = false,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LifeEpisodesUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun showMemoriesForEpisode(episode: LifeEpisode?) {
        _viewingMemoriesForEpisode.value = episode
    }

    fun setSelectedSeasonAndEpisode(episodeId: String) {
        lifeSeasonRepository.setActiveSeasonAndEpisode(seasonId, episodeId)
    }

    fun setActiveEpisode(episodeId: String) {
        lifeSeasonRepository.setActiveSeasonAndEpisode(seasonId, episodeId)
    }

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
            // 1. Bölümü kaydediyoruz
            saveEpisodeUseCase(newEpisode)

            // 2. Yeni eklenen bölümü otomatik olarak aktif/seçili bölüm yapıyoruz
            lifeSeasonRepository.setActiveSeasonAndEpisode(seasonId, newEpisodeId)
        }
    }
    fun deleteEpisode(episode: LifeEpisode) {
        viewModelScope.launch {
            deleteEpisodeUseCase(episode)
        }
    }
}