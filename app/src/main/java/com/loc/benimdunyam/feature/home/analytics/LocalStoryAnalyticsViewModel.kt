package com.loc.benimdunyam.feature.home.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** UI her filtre değişiminde Room'a yeni sorgu atmaz; mevcut Flow'ları birlikte işler. */
data class LocalAnalyticsUiState(
    val isLoading: Boolean = true,
    val report: LocalStoryAnalyticsReport = LocalStoryAnalyticsReport(),
    val errorMessage: String? = null
)

@HiltViewModel
class LocalStoryAnalyticsViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    moodRepository: JournalMoodRepository
) : ViewModel() {
    private val selectedPeriod = MutableStateFlow(LocalAnalyticsPeriod.LAST_12_MONTHS)

    fun selectPeriod(period: LocalAnalyticsPeriod) {
        selectedPeriod.value = period
    }

    val uiState: StateFlow<LocalAnalyticsUiState> = combine(
        observeJournalsUseCase(),
        observeLifeSeasonsUseCase(),
        moodRepository.observeAll(),
        selectedPeriod
    ) { journals, seasons, moods, period ->
        // Ruh halleri ayrı Room'da tutulur; olmayan kimlikleri hesaplayıcı zaten dikkate alır.
        LocalAnalyticsUiState(
            isLoading = false,
            report = LocalStoryAnalyticsCalculator.calculate(
                journals = journals,
                seasons = seasons,
                moodNamesByJournalId = moods.associate { it.journalId to it.moodName },
                period = period
            )
        )
    }.flowOn(Dispatchers.Default)
        .catch {
            emit(LocalAnalyticsUiState(isLoading = false,
                errorMessage = "Yerel analizler şu anda okunamadı; anıların değiştirilmedi."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalAnalyticsUiState())
}
