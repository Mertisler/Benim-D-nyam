package com.loc.benimdunyam.feature.people

import android.content.Context
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.model.DeviceContactItem
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import com.loc.benimdunyam.domain.usecase.SavePersonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PeopleUiState(
    val searchQuery: String = "",
    val people: List<Person> = emptyList(),
    val isLoading: Boolean = false,
    val successMessage: String? = null
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    observePeopleUseCase: ObservePeopleUseCase,
    private val savePersonUseCase: SavePersonUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _allPeople = observePeopleUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _uiMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PeopleUiState> = combine(
        _searchQuery,
        _allPeople,
        _uiMessage
    ) { query, people, message ->
        val filtered = if (query.isBlank()) {
            people
        } else {
            people.filter { it.name.contains(query, ignoreCase = true) }
        }
        PeopleUiState(
            searchQuery = query,
            people = filtered,
            isLoading = false,
            successMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PeopleUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // Cihaz rehberindeki kişileri okuma
    fun fetchDeviceContacts(context: Context): List<DeviceContactItem> {
        val contactList = mutableListOf<DeviceContactItem>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val cursor = context.contentResolver.query(
            uri,
            null,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = if (idIndex != -1) it.getString(idIndex) else ""
                val name = if (nameIndex != -1) it.getString(nameIndex) else ""
                if (!name.isNullOrBlank()) {
                    contactList.add(DeviceContactItem(id = id, name = name))
                }
            }
        }
        return contactList
    }

    // Çoklu kişi aktarımı (Toplu ekleme ve duplicate kontrolü)
    fun importMultipleContacts(selectedNames: List<String>, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch {
            var addedCount = 0
            var duplicateCount = 0

            val currentPeople = _allPeople.value

            selectedNames.forEach { name ->
                val trimmedName = name.trim()
                if (trimmedName.isNotEmpty()) {
                    val exists = currentPeople.any { it.name.equals(trimmedName, ignoreCase = true) }
                    if (exists) {
                        duplicateCount++
                    } else {
                        val newPerson = Person(
                            id = UUID.randomUUID().toString(),
                            name = trimmedName,
                            note = "Cihaz rehberinden toplu aktarıldı",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        savePersonUseCase(newPerson, isUpdate = false)
                        addedCount++
                    }
                }
            }
            onResult(addedCount, duplicateCount)
        }
    }
}