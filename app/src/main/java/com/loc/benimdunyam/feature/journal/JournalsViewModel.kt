package com.loc.benimdunyam.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.DeleteJournalWithPhotosUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import com.loc.benimdunyam.feature.journal.filter.JournalArchiveFilterEngine
import com.loc.benimdunyam.feature.journal.filter.JournalArchiveFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JournalsUiState(
    val journals: List<JournalEntry> = emptyList(),
    val totalJournals: Int = 0,
    val seasons: List<LifeSeason> = emptyList(),
    val episodes: List<LifeEpisode> = emptyList(),
    // Every journal can belong to a different season: these titles are not tied to the filter.
    val episodeTitles: Map<String, String> = emptyMap(),
    val people: List<Person> = emptyList(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val searchQuery: String = "",
    val selectedSeasonId: String? = null,
    val selectedEpisodeId: String? = null,
    val selectedPersonId: String? = null,
    val hasActiveFilters: Boolean = false,
    val isLoading: Boolean = true,
    val deleteErrorMessage: String? = null
)

private data class JournalArchiveData(
    val journals: List<JournalEntry>,
    val seasons: List<LifeSeason>,
    val people: List<Person>,
    val episodes: List<LifeEpisode>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalsViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observePeopleUseCase: ObservePeopleUseCase,
    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase,
    private val deleteJournalUseCase: DeleteJournalWithPhotosUseCase
) : ViewModel() {

    private val filters = MutableStateFlow(JournalArchiveFilters())
    private val deleteError = MutableStateFlow<String?>(null)
    private val turkishLocale = Locale.forLanguageTag("tr-TR")

    // Episodes are only collected for the selected season. Switching seasons
    // cancels the previous observation and clears the selected episode.
    private val selectedEpisodes = filters
        .map { it.selectedSeasonId }
        .distinctUntilChanged()
        .flatMapLatest { seasonId ->
            if (seasonId == null) flowOf(emptyList())
            else observeEpisodesBySeasonUseCase(seasonId)
        }

    private val archiveData = combine(
        observeJournalsUseCase(),
        observeLifeSeasonsUseCase(),
        observePeopleUseCase(),
        selectedEpisodes
    ) { journals, seasons, people, episodes ->
        JournalArchiveData(journals, seasons, people, episodes)
    }

    // The filter sheet observes episodes only for the selected season. Journal cards,
    // however, must display episode names from *all* seasons represented in the archive.
    // Observe just those seasons reactively; renaming an episode updates the card too.
    private val archiveEpisodeTitles = archiveData
        .map { data ->
            data.journals.mapNotNull { it.seasonId }.distinct().sorted()
        }
        .distinctUntilChanged()
        .flatMapLatest { seasonIds ->
            if (seasonIds.isEmpty()) {
                flowOf(emptyMap<String, String>())
            } else {
                combine(seasonIds.map { seasonId -> observeEpisodesBySeasonUseCase(seasonId) }) { groups ->
                    groups.flatMap { it }.associate { it.id to it.title }
                }
            }
        }

    val uiState: StateFlow<JournalsUiState> = combine(
        archiveData,
        archiveEpisodeTitles,
        filters,
        deleteError
    ) { data, episodeTitles, active, warning ->
        // Filter menu only lists people actually mentioned in at least one entry.
        // This avoids creating thousands of menu items after contact import.
        val mentionedIds = data.journals.flatMap { journal ->
            journal.mentionedPeople.map { it.id }
        }.toSet()
        val missingPeople = data.journals.flatMap { it.mentionedPeople }
        val choices = (data.people.filter { it.id in mentionedIds } + missingPeople)
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase(turkishLocale) }

        JournalsUiState(
            journals = JournalArchiveFilterEngine.filter(data.journals, active),
            totalJournals = data.journals.size,
            seasons = data.seasons,
            episodes = data.episodes.filter { it.seasonId == active.selectedSeasonId }
                .sortedBy { it.orderIndex },
            episodeTitles = episodeTitles,
            people = choices,
            startDate = active.startDate,
            endDate = active.endDate,
            searchQuery = active.searchQuery,
            selectedSeasonId = active.selectedSeasonId,
            selectedEpisodeId = active.selectedEpisodeId,
            selectedPersonId = active.selectedPersonId,
            hasActiveFilters = active.hasAny,
            isLoading = false,
            deleteErrorMessage = warning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = JournalsUiState()
    )

    fun updateSearchQuery(query: String) {
        filters.update { it.copy(searchQuery = query) }
    }

    fun clearSearch() {
        filters.update { it.copy(searchQuery = "") }
    }

    fun selectPerson(personId: String?) {
        filters.update { it.copy(selectedPersonId = personId) }
    }

    fun selectSeason(seasonId: String?) {
        filters.update {
            it.copy(selectedSeasonId = seasonId, selectedEpisodeId = null)
        }
    }

    fun selectEpisode(episodeId: String?) {
        filters.update {
            it.copy(selectedEpisodeId = if (it.selectedSeasonId == null) null else episodeId)
        }
    }

    /** Dates passed from the UI are midnight (start) / 23:59:59.999 (end). */
    fun setStartDate(date: Long?) {
        filters.update { old ->
            old.copy(
                startDate = date,
                endDate = old.endDate?.takeUnless { date != null && it < date }
            )
        }
    }

    fun setEndDate(date: Long?) {
        filters.update { old ->
            old.copy(
                endDate = date,
                startDate = old.startDate?.takeUnless { date != null && it > date }
            )
        }
    }

    // Keep the old API for compatibility with other parts of the project.
    fun setDateRange(start: Long?, end: Long?) {
        filters.update { old ->
            old.copy(
                startDate = start,
                endDate = end?.takeUnless { start != null && it < start }
            )
        }
    }

    fun clearFilters() {
        filters.value = JournalArchiveFilters()
    }

    fun deleteJournal(journalId: String) {
        viewModelScope.launch {
            deleteError.value = null
            try {
                val result = deleteJournalUseCase(journalId)
                deleteError.value = result.cleanupWarning
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                deleteError.value = error.localizedMessage ?: "Günlük silinemedi."
            }
        }
    }

    fun clearDeleteError() {
        deleteError.value = null
    }
}
