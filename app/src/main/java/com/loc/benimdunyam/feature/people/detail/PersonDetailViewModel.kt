package com.loc.benimdunyam.feature.people.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.model.PersonImageType
import com.loc.benimdunyam.domain.usecase.DeletePersonImageUseCase
import com.loc.benimdunyam.domain.usecase.DeletePersonUseCase
import com.loc.benimdunyam.domain.usecase.GetPersonByIdUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveJournalsForPersonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.SavePersonImageUseCase
import com.loc.benimdunyam.domain.usecase.SavePersonUseCase
import com.loc.benimdunyam.feature.people.detail.calculator.PersonProfileCalculator
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
    val episodes: List<LifeEpisode>
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
    observeJournalsForPersonUseCase: ObserveJournalsForPersonUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"])

    private val person = MutableStateFlow<Person?>(null)
    private val isLoading = MutableStateFlow(true)
    private val isSavingImage = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val journals = observeJournalsForPersonUseCase(personId).stateIn(
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
                flowOf(emptyList())
            } else {
                combine(episodeFlows) { episodeLists ->
                    episodeLists.flatMap { it }.distinctBy(LifeEpisode::id)
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
        episodes
    ) { currentPerson, currentJournals, currentSeasons, currentEpisodes ->
        PersonProfileSourceData(
            person = currentPerson,
            journals = currentJournals,
            seasons = currentSeasons,
            episodes = currentEpisodes
        )
    }

    private val uiSources = combine(
        profileSource,
        isLoading,
        isSavingImage,
        errorMessage
    ) { source, loading, savingImage, error ->
        val appearances = PersonProfileCalculator.buildAppearances(
            journals = source.journals,
            seasons = source.seasons,
            episodes = source.episodes
        )

        PersonDetailUiState(
            person = source.person,
            associatedJournals = source.journals.sortedByDescending { it.entryDate },
            profileStats = PersonProfileCalculator.calculateProfileStats(appearances),
            isLoading = loading,
            isSavingImage = savingImage,
            errorMessage = error
        )
    }

    val uiState: StateFlow<PersonDetailUiState> = uiSources.stateIn(
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
