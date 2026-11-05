
package com.loc.benimdunyam.feature.seasons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
import com.loc.benimdunyam.domain.usecase.DeleteLifeSeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.SaveLifeSeasonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val activeSeasonId: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class LifeSeasonsViewModel @Inject constructor(
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeJournalsUseCase: ObserveJournalsUseCase,
    private val saveLifeSeasonUseCase: SaveLifeSeasonUseCase,
    private val deleteLifeSeasonUseCase: DeleteLifeSeasonUseCase,
    private val lifeSeasonRepository: LifeSeasonRepository
) : ViewModel() {

    // --------------------------------------------------
    // UI STATE
    // --------------------------------------------------

    val uiState: StateFlow<LifeSeasonsUiState> = combine(
        observeLifeSeasonsUseCase(),
        observeJournalsUseCase(),
        lifeSeasonRepository.activeSeasonId
    ) { seasons, journals, activeSeasonId ->

        // En son oluşturulan yaşam dönemi ilk sırada.
        val summaries = seasons
            .sortedByDescending { it.createdAt }
            .map { season ->

                // İlgili yaşam döneminin anıları
                val seasonJournals = journals.filter { journal ->

                    val matchesId = journal.seasonId == season.id

                    val matchesDate =
                        journal.entryDate >= season.startDate &&
                                (
                                        season.endDate == null ||
                                                journal.entryDate <= season.endDate
                                        )

                    matchesId || matchesDate
                }

                // Toplam anı sayısı
                val journalCount = seasonJournals.size

                // Toplam kelime sayısı
                val totalWords = seasonJournals.sumOf { journal ->

                    journal.content
                        .trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                        .size
                }

                // En çok bahsedilen kişiler
                val personCounts = mutableMapOf<Person, Int>()

                seasonJournals.forEach { journal ->

                    journal.mentionedPeople.forEach { person ->

                        personCounts[person] =
                            (personCounts[person] ?: 0) + 1
                    }
                }

                // İlk 3 kişi
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
            activeSeasonId = activeSeasonId,
            isLoading = false
        )

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LifeSeasonsUiState(isLoading = true)
    )

    // --------------------------------------------------
    // AKTİF YAŞAM DÖNEMİ SEÇME
    // --------------------------------------------------

    fun selectSeason(seasonId: String) {

        lifeSeasonRepository.setActiveSeasonAndEpisode(
            seasonId,
            null
        )
    }

    // --------------------------------------------------
    // YENİ YAŞAM DÖNEMİ OLUŞTURMA
    // --------------------------------------------------

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

            // Yeni oluşturulan dönemi aktif yap.
            lifeSeasonRepository.setActiveSeasonAndEpisode(
                newSeason.id,
                null
            )
        }
    }

    // --------------------------------------------------
    // YAŞAM DÖNEMİ SİLME
    // --------------------------------------------------

    fun deleteSeason(season: LifeSeason) {

        viewModelScope.launch {

            deleteLifeSeasonUseCase(season)
        }
    }
}
