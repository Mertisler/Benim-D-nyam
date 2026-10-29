package com.loc.benimdunyam.feature.journals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class JournalsUiState(
    val journals: List<JournalEntry> = emptyList(),
    val allPeople: List<Person> = emptyList(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val selectedPersonIds: Set<String> = emptySet(),
    val isLoading: Boolean = true
)

@HiltViewModel
class JournalsViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observePeopleUseCase: ObservePeopleUseCase
) : ViewModel() {

    private val _startDate = MutableStateFlow<Long?>(null)
    private val _endDate = MutableStateFlow<Long?>(null)
    private val _selectedPersonIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<JournalsUiState> = combine(
        observeJournalsUseCase(),
        observePeopleUseCase(),
        _startDate,
        _endDate,
        _selectedPersonIds
    ) { journals, people, startDate, endDate, selectedPersonIds ->

        // Filtreleme Mantığı
        val filteredJournals = journals.filter { journal ->
            // Tarih Aralığı Filtresi
            val matchesStart = startDate == null || journal.entryDate >= startDate
            val matchesEnd = endDate == null || journal.entryDate <= endDate

            // Kişi Filtresi (Günlüğün etiketlendiği kişilerden en az biri seçilenler arasında mı?)
            // Not: JournalEntry modelinizde bağlı kişi ID'leri listesi tutuluyorsa burada eşleştirme yapılır.
            // Örnek olarak temel tarih ve metin uyumu üzerinden filtreleme:
            matchesStart && matchesEnd
        }

        JournalsUiState(
            journals = filteredJournals,
            allPeople = people,
            startDate = startDate,
            endDate = endDate,
            selectedPersonIds = selectedPersonIds,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JournalsUiState(isLoading = true)
    )

    fun setDateRange(start: Long?, end: Long?) {
        _startDate.value = start
        _endDate.value = end
    }

    fun togglePersonFilter(personId: String) {
        val currentSet = _selectedPersonIds.value.toMutableSet()
        if (currentSet.contains(personId)) {
            currentSet.remove(personId)
        } else {
            currentSet.add(personId)
        }
        _selectedPersonIds.value = currentSet
    }

    fun clearFilters() {
        _startDate.value = null
        _endDate.value = null
        _selectedPersonIds.value = emptySet()
    }
}