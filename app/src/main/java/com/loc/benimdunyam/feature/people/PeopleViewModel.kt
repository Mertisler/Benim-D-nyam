package com.loc.benimdunyam.feature.people

import android.content.Context
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.model.DeviceContactItem
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import com.loc.benimdunyam.domain.usecase.backupwrite.ImportPhoneContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bu ad, eski PeopleUiState ile olasi isim cakismasini onler. */
data class PeopleDisplayState(
    val people: List<Person> = emptyList(),
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val isReadingContacts: Boolean = false,
    val isRestoring: Boolean = false,
    val importError: String? = null
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    observePeopleUseCase: ObservePeopleUseCase,
    private val importPhoneContactsUseCase: ImportPhoneContactsUseCase,
    writeCoordinator: BackupWriteCoordinator
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val importing = MutableStateFlow(false)
    private val readingContacts = MutableStateFlow(false)
    private val importError = MutableStateFlow<String?>(null)

    private val listState = combine(
        observePeopleUseCase(),
        searchQuery,
        importing
    ) { people, query, busy ->
        Triple(people, query, busy)
    }

    private val operationState = combine(
        readingContacts,
        importError,
        writeCoordinator.isRestoring
    ) { reading, error, restoring ->
        Triple(reading, error, restoring)
    }

    val uiState: StateFlow<PeopleDisplayState> = combine(
        listState,
        operationState
    ) { list, operation ->
        val (people, query, busy) = list
        val (reading, error, restoring) = operation

        val filtered = if (query.isBlank()) {
            people
        } else {
            people.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

        PeopleDisplayState(
            people = filtered.sortedBy { it.name.lowercase() },
            searchQuery = query,
            isImporting = busy,
            isReadingContacts = reading,
            isRestoring = restoring,
            importError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PeopleDisplayState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun clearImportError() {
        importError.value = null
    }

    /**
     * READ_CONTACTS izni arayuz tarafindan alindikten sonra cagirilir.
     * Mevcut PeopleScreen API'siyle ayni imza korunur.
     */
    fun fetchDeviceContacts(context: Context): List<DeviceContactItem> {
        val contacts = mutableListOf<DeviceContactItem>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )

        try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(
                    ContactsContract.Contacts._ID
                )
                val nameIndex = cursor.getColumnIndex(
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                )

                while (cursor.moveToNext()) {
                    val id = if (idIndex >= 0) cursor.getString(idIndex) else null
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    if (!id.isNullOrBlank() && !name.isNullOrBlank()) {
                        contacts.add(DeviceContactItem(id = id, name = name))
                    }
                }
            }
        } catch (e: SecurityException) {
            importError.value = "Rehberi okumak icin erisim izni gerekiyor."
        } catch (e: Exception) {
            importError.value = "Rehber okunamadi: ${e.localizedMessage ?: "Bilinmeyen hata"}"
        }

        return contacts
    }

    /** Rehber okuma islemini Main thread disinda tamamlar. */
    fun loadDeviceContacts(
        context: Context,
        onLoaded: (List<DeviceContactItem>) -> Unit
    ) {
        if (readingContacts.value || uiState.value.isRestoring) return
        readingContacts.value = true
        importError.value = null
        viewModelScope.launch {
            try {
                val contacts = withContext(Dispatchers.IO) {
                    fetchDeviceContacts(context.applicationContext)
                }
                onLoaded(contacts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importError.value = e.localizedMessage ?: "Rehber okunamadi."
            } finally {
                readingContacts.value = false
            }
        }
    }

    /**
     * Eski PeopleScreen cagrisi aynen calisir:
     * importMultipleContacts(selectedNames) { added, duplicates -> ... }
     *
     * Callback yalnizca basarili transaction sonrasinda cagrilir.
     */
    fun importMultipleContacts(
        selectedNames: List<String>,
        onResult: (added: Int, duplicates: Int) -> Unit
    ) {
        if (importing.value || uiState.value.isRestoring) return

        importing.value = true
        importError.value = null

        viewModelScope.launch {
            try {
                val result = importPhoneContactsUseCase(selectedNames)
                onResult(result.added, result.duplicates)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importError.value = e.localizedMessage
                    ?: "Rehber kisileri aktarilamadi."
            } finally {
                importing.value = false
            }
        }
    }
}
