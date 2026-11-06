package com.loc.benimdunyam.feature.people.detail.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.*
import com.loc.benimdunyam.domain.usecase.*
import com.loc.benimdunyam.feature.people.detail.analytics.calculator.PersonAnalyticsCalculator
import com.loc.benimdunyam.feature.people.detail.analytics.model.PersonAnalyticsFilters
import com.loc.benimdunyam.feature.people.detail.calculator.PersonProfileCalculator
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private data class AnalyticsJournalSources(
    val journals: List<JournalEntry>,
    val seasons: List<LifeSeason>,
    val episodes: List<LifeEpisode>
)

private data class AnalyticsRelatedSources(
    val person: Person?,
    val highlights: List<PersonSpecialMemory>,
    val connections: List<PersonConnection>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonAnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPersonByIdUseCase: GetPersonByIdUseCase,
    observeJournalsForPersonUseCase: ObserveJournalsForPersonUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase,
    observePersonSpecialMemoriesUseCase: ObservePersonSpecialMemoriesUseCase,
    observePersonConnectionsUseCase: ObservePersonConnectionsUseCase
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"]) {
        "Kişi analizi için personId navigation argümanı gerekli."
    }

    private val person = MutableStateFlow<Person?>(null)
    private val isLoading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val filters = MutableStateFlow(PersonAnalyticsFilters())

    private val seasons = observeLifeSeasonsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val episodes: StateFlow<List<LifeEpisode>> = seasons
        .flatMapLatest { current ->
            val flows = current.map { observeEpisodesBySeasonUseCase(it.id) }
            if (flows.isEmpty()) flowOf(emptyList<LifeEpisode>())
            else combine(flows) { nested -> nested.flatMap { it }.distinctBy { it.id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val journalSources = combine(
        observeJournalsForPersonUseCase(personId), seasons, episodes
    ) { journals, currentSeasons, currentEpisodes ->
        AnalyticsJournalSources(journals, currentSeasons, currentEpisodes)
    }

    private val relatedSources = combine(
        person,
        observePersonSpecialMemoriesUseCase(personId),
        observePersonConnectionsUseCase(personId)
    ) { currentPerson, highlights, connections ->
        AnalyticsRelatedSources(currentPerson, highlights, connections)
    }

    private val data = combine(journalSources, relatedSources) { source, related ->
        val appearances = PersonProfileCalculator.buildAppearances(
            source.journals, source.seasons, source.episodes
        )
        appearances to related
    }

    val uiState: StateFlow<PersonAnalyticsUiState> = combine(
        data, filters, isLoading, error
    ) { (appearances, related), currentFilters, loading, currentError ->
        PersonAnalyticsUiState(
            personName = related.person?.name.orEmpty(),
            isLoading = loading,
            errorMessage = currentError,
            filters = currentFilters,
            availableYears = PersonAnalyticsCalculator.availableYears(appearances),
            availableSeasons = PersonAnalyticsCalculator.availableSeasons(appearances),
            report = PersonAnalyticsCalculator.calculate(
                appearances = appearances,
                specialMemories = related.highlights,
                connections = related.connections,
                filters = currentFilters
            ),
            hasAnyMemories = appearances.isNotEmpty()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PersonAnalyticsUiState()
    )

    init { refreshPerson() }

    fun refreshPerson() {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            try {
                person.value = getPersonByIdUseCase(personId)
                if (person.value == null) error.value = "Kişi bulunamadı."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Analiz bilgileri yüklenemedi."
            } finally {
                isLoading.value = false
            }
        }
    }

    fun selectYear(year: Int?) { filters.value = filters.value.copy(year = year) }
    fun selectSeason(seasonId: String?) { filters.value = filters.value.copy(seasonId = seasonId) }

    fun setStartDate(millis: Long?) {
        val start = millis?.let(PersonAnalyticsCalculator::startOfDay)
        val old = filters.value
        filters.value = old.copy(
            startDateMillis = start,
            endDateMillis = old.endDateMillis?.takeIf { start == null || it >= start }
        )
    }

    fun setEndDate(millis: Long?) {
        val end = millis?.let(PersonAnalyticsCalculator::startOfDay)
        val old = filters.value
        filters.value = old.copy(
            startDateMillis = old.startDateMillis?.takeIf { end == null || it <= end },
            endDateMillis = end
        )
    }

    fun resetFilters() { filters.value = PersonAnalyticsFilters() }
}
