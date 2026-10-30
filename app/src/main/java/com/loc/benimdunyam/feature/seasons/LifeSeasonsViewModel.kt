package com.loc.benimdunyam.feature.seasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.DeleteLifeSeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.SaveLifeSeasonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class LifeSeasonSummary(
    val season: LifeSeason,
    val journalCount: Int,
    val totalWordCount: Int,
    val topMentionedPeople: List<Person>
)

data class LifeSeasonsUiState(
    val seasonSummaries: List<LifeSeasonSummary> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class LifeSeasonsViewModel @Inject constructor(
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeJournalsUseCase: ObserveJournalsUseCase,
    private val saveLifeSeasonUseCase: SaveLifeSeasonUseCase,
    private val deleteLifeSeasonUseCase: DeleteLifeSeasonUseCase
) : ViewModel() {

    val uiState: StateFlow<LifeSeasonsUiState> = combine(
        observeLifeSeasonsUseCase(),
        observeJournalsUseCase()
    ) { seasons, journals ->
        val summaries = seasons.sortedBy { it.startDate }.map { season ->
            val seasonJournals = journals.filter { journal ->
                val matchesId = journal.seasonId == season.id
                val matchesDate = journal.entryDate >= season.startDate &&
                        (season.endDate == null || journal.entryDate <= season.endDate)
                matchesId || matchesDate
            }

            val journalCount = seasonJournals.size

            val totalWords = seasonJournals.sumOf { journal ->
                journal.content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
            }

            val personCounts = mutableMapOf<Person, Int>()
            seasonJournals.forEach { journal ->
                journal.mentionedPeople.forEach { person ->
                    personCounts[person] = (personCounts[person] ?: 0) + 1
                }
            }
            val topPeople = personCounts.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

            LifeSeasonSummary(
                season = season,
                journalCount = journalCount,
                totalWordCount = totalWords,
                topMentionedPeople = topPeople
            )
        }

        LifeSeasonsUiState(
            seasonSummaries = summaries,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LifeSeasonsUiState(isLoading = true)
    )

    fun createSeason(
        title: String,
        description: String,
        startDate: Long,
        endDate: Long?,
        colorHex: String,
        coverImagePath: String?
    ) {
        viewModelScope.launch {
            val newSeason = LifeSeason(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                startDate = startDate,
                endDate = endDate,
                colorHex = colorHex,
                coverImagePath = coverImagePath,
                createdAt = System.currentTimeMillis()
            )
            saveLifeSeasonUseCase(newSeason)
        }
    }

    fun deleteSeason(season: LifeSeason) {
        viewModelScope.launch {
            deleteLifeSeasonUseCase(season)
        }
    }
}