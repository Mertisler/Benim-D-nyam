
package com.loc.benimdunyam.feature.journal.journaleditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.Person

import com.loc.benimdunyam.domain.usecase.GetJournalByIdUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import com.loc.benimdunyam.domain.usecase.SaveJournalUseCase

import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import java.util.UUID

import javax.inject.Inject


@HiltViewModel
class JournalEditorViewModel @Inject constructor(

    savedStateHandle: SavedStateHandle,

    private val getJournalByIdUseCase: GetJournalByIdUseCase,

    private val saveJournalUseCase: SaveJournalUseCase,

    private val observePeopleUseCase: ObservePeopleUseCase,

    private val observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,

    private val observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase

) : ViewModel() {


    // =================================================
    // NAVIGATION ARGUMENTS
    // =================================================

    private val journalId: String? =
        savedStateHandle.get<String>("journalId")

    private val initialSeasonId: String? =
        savedStateHandle.get<String>("seasonId")

    private val initialEpisodeId: String? =
        savedStateHandle.get<String>("episodeId")


    // =================================================
    // UI STATE
    // =================================================

    private val _uiState = MutableStateFlow(
        JournalEditorUiState()
    )

    val uiState: StateFlow<JournalEditorUiState> =
        _uiState


    // Düzenlenen günlüğün orijinal oluşturulma tarihi.
    private var originalCreatedAt: Long? = null


    // =================================================
    // INITIALIZATION
    // =================================================

    init {

        observePeople()

        observeSeasons()

        observeEpisodes()

        when {

            // Mevcut günlük düzenleniyor.
            !journalId.isNullOrBlank() -> {
                loadJournal(journalId)
            }

            // Yeni günlük belirli bir sezonla açılıyor.
            !initialSeasonId.isNullOrBlank() -> {

                _uiState.update { currentState ->

                    currentState.copy(
                        selectedSeasonId = initialSeasonId,
                        selectedEpisodeId = initialEpisodeId
                    )
                }
            }
        }
    }


    // =================================================
    // PEOPLE OBSERVATION
    // =================================================

    private fun observePeople() {

        viewModelScope.launch {

            observePeopleUseCase()

                .catch { exception ->

                    _uiState.update { currentState ->

                        currentState.copy(
                            errorMessage = "Kişiler yüklenemedi."
                        )
                    }

                    emit(emptyList())
                }

                .collectLatest { people ->

                    _uiState.update { currentState ->

                        currentState.copy(
                            allPeople = people
                        )
                    }
                }
        }
    }


    // =================================================
    // SEASON OBSERVATION
    // =================================================

    private fun observeSeasons() {

        viewModelScope.launch {

            observeLifeSeasonsUseCase()

                .catch { exception ->

                    _uiState.update { currentState ->

                        currentState.copy(
                            errorMessage = "Dönemler yüklenemedi."
                        )
                    }

                    emit(emptyList())
                }

                .collectLatest { seasons ->

                    _uiState.update { currentState ->

                        currentState.copy(
                            allSeasons = seasons
                        )
                    }
                }
        }
    }


    // =================================================
    // EPISODE OBSERVATION
    // =================================================

    /**
     * Seçilen sezon değiştikçe ilgili bölümleri gözlemler.
     *
     * flatMapLatest sayesinde yeni sezon seçildiğinde
     * eski sezonun Flow gözlemi iptal edilir.
     */

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeEpisodes() {

        viewModelScope.launch {

            _uiState

                .map { state ->
                    state.selectedSeasonId
                }

                .distinctUntilChanged()

                .flatMapLatest { seasonId ->

                    if (seasonId.isNullOrBlank()) {

                        flowOf(emptyList<LifeEpisode>())

                    } else {

                        observeEpisodesBySeasonUseCase(seasonId)
                    }
                }

                .catch { exception ->

                    _uiState.update { currentState ->

                        currentState.copy(
                            currentSeasonEpisodes = emptyList(),
                            errorMessage = "Bölümler yüklenemedi."
                        )
                    }

                    emit(emptyList())
                }

                .collectLatest { episodes ->

                    _uiState.update { currentState ->

                        val selectedEpisodeStillExists =
                            currentState.selectedEpisodeId?.let { selectedId ->

                                episodes.any { episode ->
                                    episode.id == selectedId
                                }

                            } ?: true

                        currentState.copy(
                            currentSeasonEpisodes = episodes,

                            selectedEpisodeId = if (
                                episodes.isNotEmpty() &&
                                !selectedEpisodeStillExists
                            ) {
                                null
                            } else {
                                currentState.selectedEpisodeId
                            }
                        )
                    }
                }
        }
    }


    // =================================================
    // LOAD JOURNAL
    // =================================================

    private fun loadJournal(id: String) {

        viewModelScope.launch {

            _uiState.update { currentState ->

                currentState.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            try {

                val journal = getJournalByIdUseCase(id)

                if (journal == null) {

                    _uiState.update { currentState ->

                        currentState.copy(
                            isLoading = false,
                            errorMessage = "Günlük bulunamadı."
                        )
                    }

                    return@launch
                }

                // Orijinal oluşturulma tarihini koru.
                originalCreatedAt = journal.createdAt

                _uiState.update { currentState ->

                    currentState.copy(

                        title = journal.title,

                        content = journal.content,

                        entryDate = journal.entryDate,

                        mentionedPeople = journal.mentionedPeople,

                        selectedSeasonId = journal.seasonId,

                        selectedEpisodeId = journal.episodeId,

                        isLoading = false,

                        errorMessage = null
                    )
                }

            } catch (exception: CancellationException) {

                throw exception

            } catch (exception: Exception) {

                _uiState.update { currentState ->

                    currentState.copy(
                        isLoading = false,
                        errorMessage = "Günlük yüklenirken bir hata oluştu."
                    )
                }
            }
        }
    }


    // =================================================
    // TITLE
    // =================================================

    fun onTitleChanged(newTitle: String) {

        _uiState.update { currentState ->

            currentState.copy(
                title = newTitle,
                errorMessage = null
            )
        }
    }


    // =================================================
    // CONTENT
    // =================================================

    fun onContentChanged(newContent: String) {

        val mention = JournalMentionHandler.detect(
            newContent
        )

        _uiState.update { currentState ->

            currentState.copy(

                content = newContent,

                isMentionPopupVisible = mention.isVisible,

                mentionQuery = if (mention.isVisible) {
                    mention.query
                } else {
                    ""
                },

                errorMessage = null
            )
        }
    }


    // =================================================
    // DATE
    // =================================================

    fun onDateChanged(newDate: Long) {

        _uiState.update { currentState ->

            currentState.copy(
                entryDate = newDate,
                errorMessage = null
            )
        }
    }


    // =================================================
    // MOOD
    // =================================================

    fun onMoodSelected(mood: JournalMood?) {

        _uiState.update { currentState ->

            currentState.copy(
                selectedMood = mood
            )
        }
    }


    // =================================================
    // SEASON
    // =================================================

    fun onSeasonSelected(seasonId: String?) {

        val normalizedSeasonId = seasonId
            ?.takeIf { it.isNotBlank() }

        _uiState.update { currentState ->

            // Aynı sezon seçilmişse mevcut bölümleri koru.
            if (currentState.selectedSeasonId == normalizedSeasonId) {
                currentState
            } else {

                currentState.copy(

                    selectedSeasonId = normalizedSeasonId,

                    selectedEpisodeId = null,

                    currentSeasonEpisodes = emptyList(),

                    errorMessage = null
                )
            }
        }
    }


    // =================================================
    // EPISODE
    // =================================================

    fun onEpisodeSelected(episodeId: String?) {

        _uiState.update { currentState ->

            currentState.copy(
                selectedEpisodeId = episodeId,
                errorMessage = null
            )
        }
    }


    // =================================================
    // SELECT PERSON FOR MENTION
    // =================================================

    fun selectPersonForMention(person: Person) {

        val currentState = _uiState.value

        val updatedContent = JournalMentionHandler.replaceMention(
            content = currentState.content,
            personName = person.name
        )

        // Aynı kişi birden fazla kez eklenmesin.
        val updatedPeople = if (
            currentState.mentionedPeople.any { it.id == person.id }
        ) {

            currentState.mentionedPeople

        } else {

            currentState.mentionedPeople + person
        }

        _uiState.update { state ->

            state.copy(

                content = updatedContent,

                mentionedPeople = updatedPeople,

                isMentionPopupVisible = false,

                mentionQuery = ""
            )
        }
    }


    // =================================================
    // REMOVE MENTIONED PERSON
    // =================================================

    fun removeMentionedPerson(person: Person) {

        _uiState.update { currentState ->

            currentState.copy(

                mentionedPeople = currentState.mentionedPeople.filter { item ->
                    item.id != person.id
                }
            )
        }
    }


    // =================================================
    // CLEAR ERROR
    // =================================================

    fun clearError() {

        _uiState.update { currentState ->

            currentState.copy(
                errorMessage = null
            )
        }
    }


    // =================================================
    // SAVE JOURNAL
    // =================================================

    fun saveJournal(
        onSuccess: () -> Unit
    ) {

        val currentState = _uiState.value

        // Çift kaydetme işlemini engelle.
        if (currentState.isSaving || currentState.isLoading) {
            return
        }

        val title = currentState.title.trim()

        val content = currentState.content.trim()

        // Form doğrulaması.
        if (title.isEmpty() || content.isEmpty()) {

            _uiState.update { state ->

                state.copy(
                    errorMessage = "Başlık ve içerik boş bırakılamaz."
                )
            }

            return
        }

        _uiState.update { state ->

            state.copy(
                isSaving = true,
                isSaved = false,
                errorMessage = null
            )
        }

        viewModelScope.launch {

            try {

                val currentTime = System.currentTimeMillis()

                val currentId = journalId
                    ?: UUID.randomUUID().toString()

                // Düzenleme sırasında createdAt değişmez.
                val createdAt = originalCreatedAt ?: currentTime

                val journal = JournalEntry(

                    id = currentId,

                    title = title,

                    content = content,

                    entryDate = currentState.entryDate,

                    seasonId = currentState.selectedSeasonId,

                    episodeId = currentState.selectedEpisodeId,

                    createdAt = createdAt,

                    updatedAt = currentTime,

                    mentionedPeople = currentState.mentionedPeople
                )

                val personIds = currentState.mentionedPeople.map { person ->
                    person.id
                }

                saveJournalUseCase(
                    journal,
                    personIds
                )

                originalCreatedAt = createdAt

                _uiState.update { state ->

                    state.copy(
                        isSaving = false,
                        isSaved = true,
                        errorMessage = null
                    )
                }

                onSuccess()

            } catch (exception: CancellationException) {

                throw exception

            } catch (exception: Exception) {

                _uiState.update { state ->

                    state.copy(

                        isSaving = false,

                        isSaved = false,

                        errorMessage = exception.localizedMessage
                            ?: "Günlük kaydedilirken bir hata oluştu."
                    )
                }
            }
        }
    }
}
