package com.loc.benimdunyam.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.SearchJournalsUseCase
import com.loc.benimdunyam.domain.usecase.SearchPeopleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val journals: List<JournalEntry> = emptyList(),
    val people: List<Person> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchJournalsUseCase: SearchJournalsUseCase,
    private val searchPeopleUseCase: SearchPeopleUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> = _query
        .debounce(300) // Hızlı yazımlarda gereksiz sorguları önlemek için gecikme
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(SearchUiState(query = query))
            } else {
                combine(
                    searchJournalsUseCase(query),
                    searchPeopleUseCase(query)
                ) { journals, people ->
                    SearchUiState(
                        query = query,
                        journals = journals,
                        people = people,
                        isLoading = false
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState()
        )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }
}