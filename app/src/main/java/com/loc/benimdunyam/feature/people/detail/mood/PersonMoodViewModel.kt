package com.loc.benimdunyam.feature.people.detail.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Günlük ruh hallerinin tek kaynağını kullanır; ikinci bir Room tablosu açılmaz. */
data class PersonMoodUiState(
    val moodsByJournalId: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class PersonMoodViewModel @Inject constructor(
    journalMoodRepository: JournalMoodRepository
) : ViewModel() {

    val uiState: StateFlow<PersonMoodUiState> = journalMoodRepository.observeAll()
        .map { saved ->
            PersonMoodUiState(
                moodsByJournalId = saved.associate { it.journalId to it.moodName },
                isLoading = false
            )
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(PersonMoodUiState(isLoading = false, errorMessage = "Ruh halleri okunamadı."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PersonMoodUiState()
        )
}
