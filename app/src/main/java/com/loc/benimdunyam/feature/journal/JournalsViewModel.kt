package com.loc.benimdunyam.feature.journals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class JournalsUiState(
    val journals: List<JournalEntry> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class JournalsViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase
) : ViewModel() {

    val uiState: StateFlow<JournalsUiState> = observeJournalsUseCase()
        .map { journals ->
            JournalsUiState(
                journals = journals,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = JournalsUiState(isLoading = true)
        )
}