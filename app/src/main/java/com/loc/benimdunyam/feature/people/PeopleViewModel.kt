package com.loc.benimdunyam.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import com.loc.benimdunyam.domain.usecase.SearchPeopleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class PeopleUiState(
    val searchQuery: String = "",
    val people: List<Person> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    observePeopleUseCase: ObservePeopleUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _allPeople = observePeopleUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<PeopleUiState> = combine(
        _searchQuery,
        _allPeople
    ) { query, people ->
        val filtered = if (query.isBlank()) {
            people
        } else {
            people.filter { it.name.contains(query, ignoreCase = true) }
        }
        PeopleUiState(
            searchQuery = query,
            people = filtered,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PeopleUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}