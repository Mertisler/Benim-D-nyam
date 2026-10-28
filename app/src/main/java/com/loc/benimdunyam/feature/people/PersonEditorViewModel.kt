package com.loc.benimdunyam.feature.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.GetPersonByIdUseCase
import com.loc.benimdunyam.domain.usecase.SavePersonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PersonEditorUiState(
    val name: String = "",
    val note: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class PersonEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPersonByIdUseCase: GetPersonByIdUseCase,
    private val savePersonUseCase: SavePersonUseCase
) : ViewModel() {

    private val personId: String? = savedStateHandle.get<String>("personId")
    val isEditing: Boolean = !personId.isNullOrBlank()

    var uiState by mutableStateOf(PersonEditorUiState())
        private set

    init {
        if (isEditing && personId != null) {
            loadPerson(personId)
        }
    }

    private fun loadPerson(id: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val person = getPersonByIdUseCase(id)
            if (person != null) {
                uiState = uiState.copy(
                    name = person.name,
                    note = person.note ?: "",
                    isLoading = false
                )
            } else {
                uiState = uiState.copy(isLoading = false, errorMessage = "Kişi bulunamadı.")
            }
        }
    }

    fun onNameChanged(newName: String) {
        uiState = uiState.copy(name = newName, errorMessage = null)
    }

    fun onNoteChanged(newNote: String) {
        uiState = uiState.copy(note = newNote)
    }

    fun savePerson(onSuccess: () -> Unit) {
        val trimmedName = uiState.name.trim()
        if (trimmedName.isEmpty()) {
            uiState = uiState.copy(errorMessage = "İsim alanı boş bırakılamaz.") // Boş isim doğrulaması[cite: 6, 7]
            return
        }

        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val currentId = personId ?: UUID.randomUUID().toString()

            // Eğer düzenleme modundaysa mevcut tarihleri koruyabiliriz veya güncelleyebiliriz
            val existingPerson = if (isEditing && personId != null) getPersonByIdUseCase(personId) else null

            val person = Person(
                id = currentId,
                name = trimmedName,
                note = uiState.note.ifBlank { null },
                createdAt = existingPerson?.createdAt ?: currentTime,
                updatedAt = currentTime
            )

            savePersonUseCase(person, isUpdate = isEditing)
            uiState = uiState.copy(isSaved = true)
            onSuccess()
        }
    }
}