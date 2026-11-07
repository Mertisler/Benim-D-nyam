package com.loc.benimdunyam.feature.people.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.usecase.ObservePeopleNetworkUseCase
import com.loc.benimdunyam.feature.people.network.calculator.NetworkGraphBuilder
import com.loc.benimdunyam.feature.people.network.calculator.NetworkLayoutCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

private data class NetworkFilters(
    val seasonId: String? = null,
    val onlyWithMemories: Boolean = true,
    val searchQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PeopleNetworkViewModel @Inject constructor(
    observePeopleNetworkUseCase: ObservePeopleNetworkUseCase
) : ViewModel() {

    private val filters = MutableStateFlow(NetworkFilters())
    private val selectedPersonId = MutableStateFlow<String?>(null)

    private val calculated = combine(observePeopleNetworkUseCase(), filters) { snapshot, filter ->
        snapshot to filter
    }.mapLatest { (snapshot, filter) ->
        withContext(Dispatchers.Default) {
            val effectiveSeasonId = filter.seasonId?.takeIf { id ->
                snapshot.seasons.any { it.id == id }
            }
            val rawGraph = NetworkGraphBuilder.build(
                snapshot = snapshot,
                seasonId = effectiveSeasonId,
                onlyWithMemories = filter.onlyWithMemories,
                prioritizedNameQuery = filter.searchQuery
            )
            PeopleNetworkUiState(
                isLoading = false,
                graph = NetworkLayoutCalculator.layout(rawGraph),
                availableSeasons = snapshot.seasons.sortedByDescending { it.createdAt },
                selectedSeasonId = effectiveSeasonId,
                onlyWithMemories = filter.onlyWithMemories
            )
        }
    }.catch { exception ->
        if (exception is kotlinx.coroutines.CancellationException) throw exception
        emit(PeopleNetworkUiState(isLoading = false, errorMessage = "İnsan haritası yüklenemedi."))
    }

    val uiState: StateFlow<PeopleNetworkUiState> = combine(
        calculated, selectedPersonId
    ) { state, personId ->
        state.copy(selectedPersonId = personId?.takeIf { id -> state.graph.node(id) != null })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PeopleNetworkUiState()
    )

    fun selectSeason(id: String?) {
        filters.value = filters.value.copy(seasonId = id)
        selectedPersonId.value = null
    }

    fun showOnlyWithMemories(enabled: Boolean) {
        filters.value = filters.value.copy(onlyWithMemories = enabled)
        selectedPersonId.value = null
    }

    fun setSearchQuery(query: String) {
        filters.value = filters.value.copy(searchQuery = query.take(80))
    }

    fun selectPerson(id: String) { selectedPersonId.value = id }
    fun clearSelection() { selectedPersonId.value = null }
}
