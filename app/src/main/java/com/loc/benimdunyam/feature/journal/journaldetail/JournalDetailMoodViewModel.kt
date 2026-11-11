package com.loc.benimdunyam.feature.journal.journaldetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Anı detayında seçili ruh halini ayrı Room deposundan canlı gözlemler. */
data class JournalDetailMoodUiState(
    val journalId: String? = null,
    val moodName: String? = null,
    val isUnavailable: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalDetailMoodViewModel @Inject constructor(
    private val moodRepository: JournalMoodRepository
) : ViewModel() {

    private val selectedJournalId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<JournalDetailMoodUiState> = selectedJournalId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) {
                flowOf(JournalDetailMoodUiState())
            } else {
                moodRepository.observeAll()
                    .map { records ->
                        JournalDetailMoodUiState(
                            journalId = id,
                            moodName = records.firstOrNull { it.journalId == id }?.moodName
                        )
                    }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(JournalDetailMoodUiState(journalId = id, isUnavailable = true))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = JournalDetailMoodUiState()
        )

    fun showMoodFor(journalId: String) {
        if (selectedJournalId.value != journalId) selectedJournalId.value = journalId
    }
}
