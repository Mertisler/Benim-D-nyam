
package com.loc.benimdunyam.feature.journals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.DeleteJournalUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class JournalsUiState(
    val journals: List<JournalEntry> = emptyList(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val deleteErrorMessage: String? = null
)

@HiltViewModel
class JournalsViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    private val deleteJournalUseCase: DeleteJournalUseCase
) : ViewModel() {

    // Tarih filtreleri
    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)

    // Arama metni
    private val _searchQuery = MutableStateFlow("")

    // Silme işlemi hata durumu
    private val _deleteErrorMessage = MutableStateFlow<String?>(null)

    // Türkçe karakterlere uygun büyük/küçük harf kontrolü
    private val turkishLocale = Locale("tr", "TR")

    // Günlükleri gözlemle ve filtrele
    private val baseUiState = combine(
        observeJournalsUseCase(),
        _startDate,
        _endDate,
        _searchQuery
    ) { journals, startDate, endDate, searchQuery ->

        // Arama metnini normalize et
        val normalizedQuery = searchQuery
            .trim()
            .lowercase(turkishLocale)

        // Günlükleri filtrele
        val filteredJournals = journals.filter { journal ->

            // Başlangıç tarihi kontrolü
            val matchesStart =
                startDate == null ||
                        journal.entryDate >= startDate

            // Bitiş tarihi kontrolü
            val matchesEnd =
                endDate == null ||
                        journal.entryDate <= endDate

            // Günlük başlığında arama
            val matchesTitle =
                journal.title.lowercase(turkishLocale)
                    .contains(normalizedQuery)

            // Günlük içeriğinde arama
            val matchesContent =
                journal.content.lowercase(turkishLocale)
                    .contains(normalizedQuery)

            // Etiketlenen kişilerde arama
            val matchesPeople =
                journal.mentionedPeople.any { person ->
                    person.name.lowercase(turkishLocale)
                        .contains(normalizedQuery)
                }

            // Arama kontrolü
            val matchesSearch =
                normalizedQuery.isBlank() ||
                        matchesTitle ||
                        matchesContent ||
                        matchesPeople

            matchesStart && matchesEnd && matchesSearch
        }

        JournalsUiState(
            journals = filteredJournals,
            startDate = startDate,
            endDate = endDate,
            searchQuery = searchQuery,
            isLoading = false
        )

    }

    // Ana UI State
    val uiState: StateFlow<JournalsUiState> = combine(
        baseUiState,
        _deleteErrorMessage
    ) { state, deleteError ->

        state.copy(
            deleteErrorMessage = deleteError
        )

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JournalsUiState(isLoading = true)
    )

    // Günlük silme
    fun deleteJournal(journalId: String) {

        viewModelScope.launch {

            _deleteErrorMessage.value = null

            try {

                deleteJournalUseCase(journalId)

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                _deleteErrorMessage.value =
                    e.localizedMessage ?: "Günlük silinemedi."
            }
        }
    }

    // Silme hatasını temizle
    fun clearDeleteError() {
        _deleteErrorMessage.value = null
    }

    // Tarih aralığını belirle
    fun setDateRange(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
    }

    // Arama metnini güncelle
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Sadece aramayı temizle
    fun clearSearch() {
        _searchQuery.value = ""
    }

    // Bütün filtreleri ve aramayı temizle
    fun clearFilters() {
        _startDate.value = null
        _endDate.value = null
        _searchQuery.value = ""
    }
}
