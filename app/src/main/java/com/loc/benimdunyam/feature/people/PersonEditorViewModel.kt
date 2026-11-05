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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PersonEditorUiState(
    val name: String = "",
    val note: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
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

    /** Keeps existing image paths and counters when only name/note are edited. */
    private var loadedPerson: Person? = null

    init {
        if (isEditing && personId != null) {
            loadPerson(personId)
        }
    }

    private fun loadPerson(id: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)

            try {
                val person = getPersonByIdUseCase(id)
                loadedPerson = person

                if (person != null) {
                    uiState = uiState.copy(
                        name = person.name,
                        note = person.note.orEmpty(),
                        isLoading = false
                    )
                } else {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Kişi bulunamadı."
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Kişi bilgileri yüklenemedi."
                )
            }
        }
    }

    fun onNameChanged(newName: String) {
        uiState = uiState.copy(name = newName, errorMessage = null)
    }

    fun onNoteChanged(newNote: String) {
        uiState = uiState.copy(note = newNote, errorMessage = null)
    }

    fun savePerson(onSuccess: () -> Unit) {
        val trimmedName = uiState.name.trim()

        if (trimmedName.isEmpty()) {
            uiState = uiState.copy(errorMessage = "İsim alanı boş bırakılamaz.")
            return
        }

        if (uiState.isLoading || uiState.isSaving) return

        if (isEditing && loadedPerson == null) {
            uiState = uiState.copy(
                errorMessage = "Kişi bilgileri henüz yüklenmedi. Lütfen tekrar deneyin."
            )
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, errorMessage = null)

            try {
                val currentTime = System.currentTimeMillis()
                val existingPerson = loadedPerson

                val person = if (existingPerson != null) {
                    // copy() preserves profileImagePath, coverImagePath and counters.
                    existingPerson.copy(
                        name = trimmedName,
                        note = uiState.note.ifBlank { null },
                        updatedAt = currentTime
                    )
                } else {
                    Person(
                        id = UUID.randomUUID().toString(),
                        name = trimmedName,
                        note = uiState.note.ifBlank { null },
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                }

                savePersonUseCase(person, isUpdate = isEditing)
                loadedPerson = person
                uiState = uiState.copy(isSaving = false, isSaved = true)
                onSuccess()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                uiState = uiState.copy(
                    isSaving = false,
                    errorMessage = exception.message ?: "Kişi kaydedilemedi."
                )
            }
        }
    }
}
