package com.loc.benimdunyam.feature.journal.journaldetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.DeleteJournalUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JournalDetailUiState(
    val journal: JournalEntry? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isDeleted: Boolean = false,
    val previousJournalId: String? = null,
    val nextJournalId: String? = null,
    val currentPosition: Int = 0,
    val totalJournals: Int = 0
)

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val observeJournalsUseCase: ObserveJournalsUseCase,
    private val deleteJournalUseCase: DeleteJournalUseCase
) : ViewModel() {

    private val currentJournalId = MutableStateFlow(
        checkNotNull(savedStateHandle.get<String>(JOURNAL_ID_ARGUMENT)) {
            "Navigation argument '$JOURNAL_ID_ARGUMENT' bulunamadı."
        }
    )

    private val _uiState = MutableStateFlow(JournalDetailUiState())
    val uiState: StateFlow<JournalDetailUiState> = _uiState.asStateFlow()

    /** Room/Repository Flow'undan gelen en güncel günlük listesi. */
    private var allJournals: List<JournalEntry> = emptyList()

    init {
        observeJournals()
    }

    private fun observeJournals() {
        viewModelScope.launch {
            observeJournalsUseCase()
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.localizedMessage
                                ?: "Günlükler yüklenirken bir hata oluştu."
                        )
                    }
                }
                .collect { journals ->
                    allJournals = journals
                    publishCurrentJournal()
                }
        }
    }

    /**
     * Aynı detay ekranında başka bir günlüğe geçer.
     * Yeni Navigation destination açılmaz; böylece geri tuşu arşive dönmeye devam eder.
     */
    fun openJournal(journalId: String) {
        if (journalId == currentJournalId.value) return

        val exists = allJournals.any { it.id == journalId }
        if (!exists) return

        currentJournalId.value = journalId
        // Ekran yeniden oluşturulursa son görüntülenen günlük geri yüklenebilsin.
        savedStateHandle[JOURNAL_ID_ARGUMENT] = journalId
        publishCurrentJournal()
    }

    private fun publishCurrentJournal() {
        // Eski -> yeni sıralama. Aynı tarihteki kayıtları id ile deterministik sıralıyoruz.
        val orderedJournals = allJournals.sortedWith(
            compareBy<JournalEntry> { it.entryDate }
                .thenBy { it.id }
        )

        val currentIndex = orderedJournals.indexOfFirst {
            it.id == currentJournalId.value
        }

        if (currentIndex == -1) {
            _uiState.update {
                it.copy(
                    journal = null,
                    isLoading = false,
                    errorMessage = "Günlük bulunamadı veya artık mevcut değil.",
                    previousJournalId = null,
                    nextJournalId = null,
                    currentPosition = 0,
                    totalJournals = orderedJournals.size
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                journal = orderedJournals[currentIndex],
                isLoading = false,
                errorMessage = null,
                isDeleted = false,
                previousJournalId = orderedJournals
                    .getOrNull(currentIndex - 1)
                    ?.id,
                nextJournalId = orderedJournals
                    .getOrNull(currentIndex + 1)
                    ?.id,
                currentPosition = currentIndex + 1,
                totalJournals = orderedJournals.size
            )
        }
    }

    fun deleteJournal(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                deleteJournalUseCase(currentJournalId.value)
                _uiState.update { it.copy(isDeleted = true) }
                onDeleted()
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = exception.localizedMessage
                            ?: "Günlük silinirken bir hata oluştu."
                    )
                }
            }
        }
    }

    private companion object {
        const val JOURNAL_ID_ARGUMENT = "journalId"
    }
}
