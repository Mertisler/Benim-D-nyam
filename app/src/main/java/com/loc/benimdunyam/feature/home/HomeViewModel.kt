package com.loc.benimdunyam.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val recentJournals: List<JournalEntry> = emptyList(),
    val totalJournalsCount: Int = 0,
    val totalPeopleCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observePeopleUseCase: ObservePeopleUseCase
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        observeJournalsUseCase(),
        observePeopleUseCase()
    ) { journals, people ->
        HomeUiState(
            recentJournals = journals.take(5),
            totalJournalsCount = journals.size,
            totalPeopleCount = people.size,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}