package com.loc.benimdunyam.feature.people.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.model.PersonImageType
import com.loc.benimdunyam.domain.model.PersonSpecialMemory
import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory
import com.loc.benimdunyam.domain.usecase.DeletePersonImageUseCase
import com.loc.benimdunyam.domain.usecase.DeletePersonSpecialMemoryUseCase
import com.loc.benimdunyam.domain.usecase.DeletePersonUseCase
import com.loc.benimdunyam.domain.usecase.GetPersonByIdUseCase
import com.loc.benimdunyam.domain.usecase.ObservePersonConnectionsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsForPersonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.ObservePersonSpecialMemoriesUseCase
import com.loc.benimdunyam.domain.usecase.SavePersonImageUseCase
import com.loc.benimdunyam.domain.usecase.SavePersonUseCase
import com.loc.benimdunyam.domain.usecase.SavePersonSpecialMemoryUseCase
import com.loc.benimdunyam.feature.people.detail.calculator.PersonProfileCalculator
import com.loc.benimdunyam.feature.people.detail.model.asUiItem
import com.loc.benimdunyam.feature.people.detail.model.PersonAppearance
import com.loc.benimdunyam.feature.people.detail.model.PersonSpecialMemoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class PersonProfileSourceData(
    val person: Person?,
    val journals: List<JournalEntry>,
    val seasons: List<LifeSeason>,
    val episodes: List<LifeEpisode>,
    val specialMemories: List<PersonSpecialMemory>
)

private data class PersonDetailFeedback(
    val isLoading: Boolean,
    val isSavingImage: Boolean,
    val isSavingSpecialMemory: Boolean,
    val errorMessage: String?,
    val specialMemoryErrorMessage: String?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPersonByIdUseCase: GetPersonByIdUseCase,
    private val deletePersonUseCase: DeletePersonUseCase,
    private val savePersonUseCase: SavePersonUseCase,
    private val savePersonImageUseCase: SavePersonImageUseCase,
    private val deletePersonImageUseCase: DeletePersonImageUseCase,
    private val savePersonSpecialMemoryUseCase: SavePersonSpecialMemoryUseCase,
    private val deletePersonSpecialMemoryUseCase: DeletePersonSpecialMemoryUseCase,
    observePersonSpecialMemoriesUseCase: ObservePersonSpecialMemoriesUseCase,
    observePersonConnectionsUseCase: ObservePersonConnectionsUseCase,
    observeJournalsForPersonUseCase: ObserveJournalsForPersonUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"])

    private val person = MutableStateFlow<Person?>(null)
    private val isLoading = MutableStateFlow(true)
    private val isSavingImage = MutableStateFlow(false)
    private val isSavingSpecialMemory = MutableStateFlow(false)
    private val specialMemoryErrorMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val journals = observeJournalsForPersonUseCase(personId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val specialMemories = observePersonSpecialMemoriesUseCase(personId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )


    // All co-appearances are queried in a single reactive Room JOIN.
    private val connections = observePersonConnectionsUseCase(personId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val seasons = observeLifeSeasonsUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val episodes: StateFlow<List<LifeEpisode>> = seasons
        .flatMapLatest { currentSeasons ->
            val episodeFlows = currentSeasons.map { season ->
                observeEpisodesBySeasonUseCase(season.id)
            }

            if (episodeFlows.isEmpty()) {
                flowOf(emptyList<LifeEpisode>())
            } else {
                combine(episodeFlows) { episodeLists ->
                    episodeLists.flatMap { it }.distinctBy { it.id }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val profileSource = combine(
        person,
        journals,
        seasons,
        episodes,
        specialMemories
    ) { currentPerson, currentJournals, currentSeasons, currentEpisodes, pinned ->
        PersonProfileSourceData(
            person = currentPerson,
            journals = currentJournals,
            seasons = currentSeasons,
            episodes = currentEpisodes,
            specialMemories = pinned
        )
    }

    private val feedback = combine(
        isLoading,
        isSavingImage,
        isSavingSpecialMemory,
        errorMessage,
        specialMemoryErrorMessage
    ) { loading, savingImage, savingSpecial, error, specialError ->
        PersonDetailFeedback(
            isLoading = loading,
            isSavingImage = savingImage,
            isSavingSpecialMemory = savingSpecial,
            errorMessage = error,
            specialMemoryErrorMessage = specialError
        )
    }

    private val uiSources = combine(profileSource, feedback) { source, status ->
        val appearances = PersonProfileCalculator.buildAppearances(
            journals = source.journals,
            seasons = source.seasons,
            episodes = source.episodes
        ).sortedWith(
            compareBy<PersonAppearance> { it.journal.entryDate }
                .thenBy { it.journal.createdAt }
                .thenBy { it.journal.id }
        )

        val journalsById = source.journals.associateBy { it.id }
        val pinnedItems = source.specialMemories.mapNotNull { memory ->
            val journal = journalsById[memory.journalId] ?: return@mapNotNull null
            PersonSpecialMemoryItem(journal = journal, memory = memory)
        }.sortedWith(
            compareByDescending<PersonSpecialMemoryItem> { it.memory.createdAt }
                .thenByDescending { it.journal.entryDate }
        )

        PersonDetailUiState(
            person = source.person,
            associatedJournals = source.journals.sortedByDescending { it.entryDate },
            appearances = appearances,
            profileStats = PersonProfileCalculator.calculateProfileStats(appearances),
            specialMemories = pinnedItems,
            isLoading = status.isLoading,
            isSavingImage = status.isSavingImage,
            isSavingSpecialMemory = status.isSavingSpecialMemory,
            errorMessage = status.errorMessage,
            specialMemoryErrorMessage = status.specialMemoryErrorMessage
        )
    }


    // Reuse journals already loaded for this person. No per-connection journal queries.
    // Also protects the UI from transient missing or deleted journal links.
    private val completeUiSource = combine(uiSources, connections) { state, related ->
        val journalsById = state.associatedJournals.associateBy { it.id }
        val appearancesByJournalId = state.appearances.associateBy { it.journal.id }
        val items = related.map { it.asUiItem(journalsById, appearancesByJournalId) }
            .filter { it.sharedJournals.isNotEmpty() }
            .sortedWith(
                compareByDescending<com.loc.benimdunyam.feature.people.detail.model.PersonConnectionItem> {
                    it.sharedMemoryCount
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.personId }
            )
        state.copy(connections = items)
    }

    val uiState: StateFlow<PersonDetailUiState> = completeUiSource.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PersonDetailUiState(isLoading = true)
    )

    init {
        loadPerson()
    }

    fun loadPerson() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                person.value = getPersonByIdUseCase(personId)
                if (person.value == null) {
                    errorMessage.value = "Kişi bulunamadı."
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                errorMessage.value = "Kişi bilgileri yüklenirken bir hata oluştu."
            } finally {
                isLoading.value = false
            }
        }
    }

    /** Copies the selected content URI into app-private storage, then persists its path. */
    fun updatePersonImage(sourceUri: String, imageType: PersonImageType) {
        val currentPerson = person.value ?: return
        if (isSavingImage.value) return

        viewModelScope.launch {
            isSavingImage.value = true
            errorMessage.value = null

            var newPath: String? = null
            var databaseUpdated = false

            val oldPath = when (imageType) {
                PersonImageType.PROFILE -> currentPerson.profileImagePath
                PersonImageType.COVER -> currentPerson.coverImagePath
            }

            try {
                newPath = savePersonImageUseCase(
                    personId = currentPerson.id,
                    sourceUri = sourceUri,
                    imageType = imageType
                )

                val updatedPerson = when (imageType) {
                    PersonImageType.PROFILE -> currentPerson.copy(profileImagePath = newPath)
                    PersonImageType.COVER -> currentPerson.copy(coverImagePath = newPath)
                }.copy(updatedAt = System.currentTimeMillis())

                savePersonUseCase(updatedPerson, isUpdate = true)
                databaseUpdated = true
                person.value = updatedPerson

                // Old image is removed only after the new path is safely stored.
                if (oldPath != newPath) {
                    runCatching { deletePersonImageUseCase(oldPath) }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                errorMessage.value = exception.message
                    ?: "Fotoğraf kaydedilemedi. Lütfen tekrar deneyin."
            } finally {
                if (!databaseUpdated && newPath != null) {
                    runCatching { deletePersonImageUseCase(newPath) }
                }
                isSavingImage.value = false
            }
        }
    }

    fun removePersonImage(imageType: PersonImageType) {
        val currentPerson = person.value ?: return
        if (isSavingImage.value) return

        viewModelScope.launch {
            isSavingImage.value = true
            errorMessage.value = null

            val oldPath = when (imageType) {
                PersonImageType.PROFILE -> currentPerson.profileImagePath
                PersonImageType.COVER -> currentPerson.coverImagePath
            }

            try {
                val updatedPerson = when (imageType) {
                    PersonImageType.PROFILE -> currentPerson.copy(profileImagePath = null)
                    PersonImageType.COVER -> currentPerson.copy(coverImagePath = null)
                }.copy(updatedAt = System.currentTimeMillis())

                savePersonUseCase(updatedPerson, isUpdate = true)
                person.value = updatedPerson
                runCatching { deletePersonImageUseCase(oldPath) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                errorMessage.value = "Fotoğraf kaldırılamadı. Lütfen tekrar deneyin."
            } finally {
                isSavingImage.value = false
            }
        }
    }

    /** Add or edit an unforgettable memory for THIS person only. */
    fun saveSpecialMemory(
        journalId: String,
        category: PersonSpecialMemoryCategory,
        note: String?,
        onSaved: () -> Unit
    ) {
        if (isSavingSpecialMemory.value) return
        if (journals.value.none { it.id == journalId }) {
            specialMemoryErrorMessage.value = "Bu anı artık bu kişiyle ilişkili değil."
            return
        }

        viewModelScope.launch {
            isSavingSpecialMemory.value = true
            specialMemoryErrorMessage.value = null
            try {
                savePersonSpecialMemoryUseCase(personId, journalId, category, note)
                onSaved()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IllegalArgumentException) {
                specialMemoryErrorMessage.value = exception.message
            } catch (exception: Exception) {
                specialMemoryErrorMessage.value =
                    "Anı işaretlenemedi. Kişi–anı bağlantısını ve veritabanını kontrol et."
            } finally {
                isSavingSpecialMemory.value = false
            }
        }
    }

    /** Only deletes the person's highlight marker, never the journal itself. */
    fun removeSpecialMemory(journalId: String, onRemoved: () -> Unit) {
        if (isSavingSpecialMemory.value) return
        viewModelScope.launch {
            isSavingSpecialMemory.value = true
            specialMemoryErrorMessage.value = null
            try {
                deletePersonSpecialMemoryUseCase(personId, journalId)
                onRemoved()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                specialMemoryErrorMessage.value = "Unutulmaz anı işareti kaldırılamadı."
            } finally {
                isSavingSpecialMemory.value = false
            }
        }
    }

    fun clearSpecialMemoryError() {
        specialMemoryErrorMessage.value = null
    }

    fun deletePerson(onDeleted: () -> Unit) {
        val currentPerson = person.value

        viewModelScope.launch {
            try {
                deletePersonUseCase(personId)
                currentPerson?.profileImagePath?.let { deletePersonImageUseCase(it) }
                currentPerson?.coverImagePath?.let { deletePersonImageUseCase(it) }
                onDeleted()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                errorMessage.value = "Kişi silinemedi. Lütfen tekrar deneyin."
            }
        }
    }
}
