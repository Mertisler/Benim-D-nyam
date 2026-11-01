package com.loc.benimdunyam.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

sealed interface SmartMemoryItem {
    data class OnThisDay(
        val journalId: String,
        val title: String,
        val snippet: String,
        val dateText: String,
        val yearsAgo: Int
    ) : SmartMemoryItem

    data class SeasonInsight(
        val season: LifeSeason,
        val topPersonName: String?,
        val journalCount: Int
    ) : SmartMemoryItem
}

data class HomeUiState(
    val recentJournals: List<JournalEntry> = emptyList(),
    val seasons: List<LifeSeason> = emptyList(),
    val activeSeasonId: String? = null,
    val activeEpisodeId: String? = null,
    val smartMemories: List<SmartMemoryItem> = emptyList(),
    val totalJournalsCount: Int = 0,
    val totalPeopleCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observePeopleUseCase: ObservePeopleUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    private val lifeSeasonRepository: LifeSeasonRepository
) : ViewModel() {

    // activeEpisodeId de combine içerisine dahil edilerek reaktif hale getirildi
    val uiState: StateFlow<HomeUiState> = combine(
        observeJournalsUseCase(),
        observePeopleUseCase(),
        observeLifeSeasonsUseCase(),
        lifeSeasonRepository.activeSeasonId,
        lifeSeasonRepository.activeEpisodeId
    ) { journals, people, seasons, activeSeasonId, activeEpisodeId ->
        val calendarToday = Calendar.getInstance()
        val currentMonth = calendarToday.get(Calendar.MONTH)
        val currentDay = calendarToday.get(Calendar.DAY_OF_MONTH)
        val currentYear = calendarToday.get(Calendar.YEAR)

        val smartMemories = mutableListOf<SmartMemoryItem>()

        // 1. "On This Day"
        journals.forEach { journal ->
            val entryCal = Calendar.getInstance().apply { timeInMillis = journal.entryDate }
            val entryMonth = entryCal.get(Calendar.MONTH)
            val entryDay = entryCal.get(Calendar.DAY_OF_MONTH)
            val entryYear = entryCal.get(Calendar.YEAR)

            if (entryMonth == currentMonth && entryDay == currentDay && entryYear < currentYear) {
                val yearsAgo = currentYear - entryYear
                val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr", "TR"))
                smartMemories.add(
                    SmartMemoryItem.OnThisDay(
                        journalId = journal.id,
                        title = journal.title.ifBlank { "Geçmişten Bir Anı" },
                        snippet = journal.content,
                        dateText = dateFormat.format(Date(journal.entryDate)),
                        yearsAgo = yearsAgo
                    )
                )
            }
        }

        if (smartMemories.isEmpty() && journals.isNotEmpty()) {
            val randomJournal = journals.minByOrNull { it.entryDate } ?: journals.first()
            val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr", "TR"))
            smartMemories.add(
                SmartMemoryItem.OnThisDay(
                    journalId = randomJournal.id,
                    title = randomJournal.title.ifBlank { "Zaman Tünelinden" },
                    snippet = randomJournal.content,
                    dateText = dateFormat.format(Date(randomJournal.entryDate)),
                    yearsAgo = 1
                )
            )
        }

        // 2. Yaşam Dönemi İçgörü Kartı
        seasons.forEach { season ->
            val seasonJournals = journals.filter { j ->
                j.seasonId == season.id || (j.entryDate >= season.startDate && (season.endDate == null || j.entryDate <= season.endDate))
            }
            if (seasonJournals.isNotEmpty()) {
                val personCounts = mutableMapOf<String, Int>()
                seasonJournals.forEach { j ->
                    j.mentionedPeople.forEach { p ->
                        personCounts[p.name] = (personCounts[p.name] ?: 0) + 1
                    }
                }
                val topPerson = personCounts.maxByOrNull { it.value }?.key

                smartMemories.add(
                    SmartMemoryItem.SeasonInsight(
                        season = season,
                        topPersonName = topPerson,
                        journalCount = seasonJournals.size
                    )
                )
            }
        }

        val resolvedSeasonId = activeSeasonId ?: seasons.maxByOrNull { it.startDate }?.id

        HomeUiState(
            recentJournals = journals.take(5),
            seasons = seasons,
            activeSeasonId = resolvedSeasonId,
            activeEpisodeId = activeEpisodeId, // Doğrudan akıştan gelen değer atanıyor
            smartMemories = smartMemories,
            totalJournalsCount = journals.size,
            totalPeopleCount = people.size,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun updateActiveSeasonAndEpisode(seasonId: String?, episodeId: String? = null) {
        lifeSeasonRepository.setActiveSeasonAndEpisode(seasonId, episodeId)
    }
}