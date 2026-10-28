package com.loc.benimdunyam.feature.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.DeleteJournalUseCase
import com.loc.benimdunyam.domain.usecase.GetJournalByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JournalDetailUiState(
    val journal: JournalEntry? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isDeleted: Boolean = false
)

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getJournalByIdUseCase: GetJournalByIdUseCase,
    private val deleteJournalUseCase: DeleteJournalUseCase
) : ViewModel() {

    private val journalId: String = checkNotNull(savedStateHandle["journalId"])

    private val _uiState = MutableStateFlow(JournalDetailUiState())
    val uiState: StateFlow<JournalDetailUiState> = _uiState.asStateFlow()

    init {
        loadJournal()
    }

    fun loadJournal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val journal = getJournalByIdUseCase(journalId)
                _uiState.update { it.copy(journal = journal, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
            }
        }
    }

    fun deleteJournal(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                deleteJournalUseCase(journalId)
                _uiState.update { it.copy(isDeleted = true) }
                onDeleted()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            }
        }
    }
}