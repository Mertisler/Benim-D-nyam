package com.loc.benimdunyam.feature.seasons.episodes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.usecase.DeleteEpisodeUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.SaveEpisodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class LifeEpisodesUiState(
    val episodes: List<LifeEpisode> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = ""
)

@HiltViewModel
class LifeEpisodesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase,
    private val saveEpisodeUseCase: SaveEpisodeUseCase,
    private val deleteEpisodeUseCase: DeleteEpisodeUseCase
) : ViewModel() {

    // Navigasyon argümanından seasonId değerini güvenli bir şekilde çekiyoruz
    private val seasonId: String = checkNotNull(savedStateHandle["seasonId"])

    private val _searchQuery = MutableStateFlow("")

    private val _episodes = observeEpisodesBySeasonUseCase(seasonId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<LifeEpisodesUiState> = combine(
        _searchQuery,
        _episodes
    ) { query, episodes ->
        val filtered = if (query.isBlank()) {
            episodes
        } else {
            episodes.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
        LifeEpisodesUiState(
            episodes = filtered,
            isLoading = false,
            searchQuery = query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LifeEpisodesUiState(isLoading = true) // DÜZELTME BURADA: '=' kullanıldı
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun createEpisode(
        title: String,
        description: String,
        orderIndex: Int,
        startDate: Long,
        endDate: Long?
    ) {
        viewModelScope.launch {
            val newEpisode = LifeEpisode(
                id = UUID.randomUUID().toString(),
                seasonId = seasonId,
                title = title,
                description = description,
                orderIndex = orderIndex,
                startDate = startDate,
                endDate = endDate,
                createdAt = System.currentTimeMillis()
            )
            saveEpisodeUseCase(newEpisode)
        }
    }

    fun deleteEpisode(episode: LifeEpisode) {
        viewModelScope.launch {
            deleteEpisodeUseCase(episode)
        }
    }
}