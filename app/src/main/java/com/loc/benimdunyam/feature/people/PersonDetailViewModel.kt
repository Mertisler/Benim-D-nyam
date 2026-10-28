package com.loc.benimdunyam.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.DeletePersonUseCase
import com.loc.benimdunyam.domain.usecase.GetPersonByIdUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsForPersonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonDetailUiState(
    val person: Person? = null,
    val associatedJournals: List<JournalEntry> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPersonByIdUseCase: GetPersonByIdUseCase,
    private val deletePersonUseCase: DeletePersonUseCase,
    observeJournalsForPersonUseCase: ObserveJournalsForPersonUseCase
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"])

    private val _personState = MutableStateFlow<Person?>(null)
    private val _journalsState = observeJournalsForPersonUseCase(personId)

    val uiState: StateFlow<PersonDetailUiState> = combine(
        _personState,
        _journalsState
    ) { person, journals ->
        PersonDetailUiState(
            person = person,
            associatedJournals = journals,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PersonDetailUiState(isLoading = true)
    )

    init {
        loadPerson()
    }

    fun loadPerson() {
        viewModelScope.launch {
            try {
                val person = getPersonByIdUseCase(personId)
                _personState.value = person
            } catch (e: Exception) {
                // Hata durumu yönetimi
            }
        }
    }

    fun deletePerson(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deletePersonUseCase(personId)
            onDeleted()
        }
    }
}