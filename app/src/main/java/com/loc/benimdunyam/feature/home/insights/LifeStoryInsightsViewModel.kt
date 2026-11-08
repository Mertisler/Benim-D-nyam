package com.loc.benimdunyam.feature.home.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn


data class LifeStoryInsightsUiState(
    val isLoading: Boolean = true,
    val report: LifeStoryInsightsReport = LifeStoryInsightsReport(),
    val errorMessage: String? = null
)

@HiltViewModel
class LifeStoryInsightsViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase
) : ViewModel() {

    // Her iki kaynak Room Flow olduğu için yeni anılar ve sezonlar otomatik yansır.
    val uiState: StateFlow<LifeStoryInsightsUiState> = combine(
        observeJournalsUseCase(),
        observeLifeSeasonsUseCase()
    ) { journals, seasons ->
        LifeStoryInsightsUiState(
            isLoading = false,
            report = LifeStoryInsightsCalculator.calculate(journals, seasons)
        )
    }.catch {
        emit(
            LifeStoryInsightsUiState(
                isLoading = false,
                errorMessage = "Hikâye içgörüleri şu anda yüklenemedi."
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LifeStoryInsightsUiState()
    )
}
