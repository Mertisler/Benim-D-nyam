package com.loc.benimdunyam.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class TimelineItem(
    val season: LifeSeason,
    val journals: List<JournalEntry>
)

data class TimelineUiState(
    val timelineItems: List<TimelineItem> = emptyList(),
    val unassignedJournals: List<JournalEntry> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeJournalsUseCase: ObserveJournalsUseCase
) : ViewModel() {

    val uiState: StateFlow<TimelineUiState> = combine(
        observeLifeSeasonsUseCase(),
        observeJournalsUseCase()
    ) { seasons, journals ->
        // Her döneme ait günlükleri eşleştir
        val timelineItems = seasons.sortedBy { it.startDate }.map { season ->
            val matchedJournals = journals.filter { journal ->
                if (!journal.seasonId.isNullOrBlank()) {
                    // 1. Öncelik: Kullanıcı manuel olarak bir dönem seçmişse, sadece o ID ile eşleşmeli
                    journal.seasonId == season.id
                } else {
                    // 2. Öncelik: Manuel seçilmemişse tarih aralığına göre eşleşmeli
                    journal.entryDate >= season.startDate &&
                            (season.endDate == null || journal.entryDate <= season.endDate)
                }
            }.sortedBy { it.entryDate }

            TimelineItem(season = season, journals = matchedJournals)
        }

        // Hiçbir döneme ait olmayan (ne manuel seçilmiş ne de tarih aralığına uyan) günlükler
        val unassigned = journals.filter { journal ->
            val hasValidManualSeason = !journal.seasonId.isNullOrBlank() && seasons.any { it.id == journal.seasonId }
            val fallsIntoAnyDateRange = seasons.any { season ->
                journal.entryDate >= season.startDate &&
                        (season.endDate == null || journal.entryDate <= season.endDate)
            }

            // Eğer geçerli bir manuel seçimi yoksa VE hiçbir tarih aralığına düşmüyorsa unassigned'dır
            !hasValidManualSeason && !fallsIntoAnyDateRange
        }.sortedBy { it.entryDate }

        TimelineUiState(
            timelineItems = timelineItems,
            unassignedJournals = unassigned,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimelineUiState(isLoading = true)
    )
}