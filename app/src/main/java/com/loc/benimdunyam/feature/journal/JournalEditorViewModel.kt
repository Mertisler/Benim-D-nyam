package com.loc.benimdunyam.feature.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.GetJournalByIdUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import com.loc.benimdunyam.domain.usecase.SaveJournalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class JournalEditorUiState(
    val title: String = "",
    val content: String = "",
    val entryDate: Long = System.currentTimeMillis(),
    val mentionedPeople: List<Person> = emptyList(),
    val allPeople: List<Person> = emptyList(),
    val allSeasons: List<LifeSeason> = emptyList(),
    val currentSeasonEpisodes: List<LifeEpisode> = emptyList(),
    val selectedSeasonId: String? = null,
    val selectedEpisodeId: String? = null,
    val isMentionPopupVisible: Boolean = false,
    val mentionQuery: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getJournalByIdUseCase: GetJournalByIdUseCase,
    private val saveJournalUseCase: SaveJournalUseCase,
    observePeopleUseCase: ObservePeopleUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    private val observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase
) : ViewModel() {

    private val journalId: String? = savedStateHandle.get<String>("journalId")

    // Dışarıdan (Dönemler/Bölümler sayfasından) gönderilen ID'leri yakalıyoruz
    private val initialSeasonId: String? = savedStateHandle.get<String>("seasonId")
    private val initialEpisodeId: String? = savedStateHandle.get<String>("episodeId")

    var uiState by mutableStateOf(JournalEditorUiState())
        private set

    init {
        // Tüm kişileri gözlemle (mention önerileri için)
        viewModelScope.launch {
            observePeopleUseCase().collectLatest { people ->
                uiState = uiState.copy(allPeople = people)
            }
        }

        // Tüm yaşam dönemlerini gözlemle
        viewModelScope.launch {
            observeLifeSeasonsUseCase().collectLatest { seasons ->
                uiState = uiState.copy(allSeasons = seasons)

                // Eğer dışarıdan bir sezon ID ile gelinmediyse ve yeni kayıt modundaysak,
                // isterseniz ilk sezonu otomatik seçili getirebilirsiniz (isteğe bağlı).
                if (initialSeasonId.isNullOrBlank() && journalId.isNullOrBlank() && seasons.isNotEmpty()) {
                    // onSeasonSelected(seasons.first().id) // İsterseniz ilkini varsayılan yapabilirsiniz
                }
            }
        }

        // Eğer dışarıdan bir sezon/bölüm ile geldiysek ve yeni kayıt modundaysak
        if (!initialSeasonId.isNullOrBlank() && journalId.isNullOrBlank()) {
            onSeasonSelected(initialSeasonId)
            if (!initialEpisodeId.isNullOrBlank()) {
                uiState = uiState.copy(selectedEpisodeId = initialEpisodeId)
            }
        }

        // Eğer düzenleme modundaysa mevcut günlüğü yükle
        if (!journalId.isNullOrBlank()) {
            loadJournal(journalId)
        }
    }

    private fun loadJournal(id: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val journal = getJournalByIdUseCase(id)
            if (journal != null) {
                if (!journal.seasonId.isNullOrBlank()) {
                    viewModelScope.launch {
                        observeEpisodesBySeasonUseCase(journal.seasonId).collectLatest { episodes ->
                            uiState = uiState.copy(currentSeasonEpisodes = episodes)
                        }
                    }
                }
                uiState = uiState.copy(
                    title = journal.title,
                    content = journal.content,
                    entryDate = journal.entryDate,
                    mentionedPeople = journal.mentionedPeople,
                    selectedSeasonId = journal.seasonId,
                    selectedEpisodeId = journal.episodeId,
                    isLoading = false
                )
            } else {
                uiState = uiState.copy(isLoading = false, errorMessage = "Günlük bulunamadı.")
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        uiState = uiState.copy(title = newTitle)
    }

    fun onContentChanged(newContent: String) {
        uiState = uiState.copy(content = newContent)

        // @ karakteri kontrolü ile kişi öneri menüsünü tetikleme (DÜZELTİLDİ: == newContent.length - 1)
        val lastAtIndex = newContent.lastIndexOf('@')
        if (lastAtIndex != -1 && lastAtIndex == newContent.length - 1) {
            uiState = uiState.copy(isMentionPopupVisible = true, mentionQuery = "")
        } else if (uiState.isMentionPopupVisible) {
            val query = newContent.substringAfterLast('@', "")
            if (query.contains(" ") || query.isEmpty()) {
                if (query.isEmpty() && !newContent.endsWith("@")) {
                    uiState = uiState.copy(isMentionPopupVisible = false)
                }
            } else {
                uiState = uiState.copy(mentionQuery = query)
            }
        }
    }

    fun onDateChanged(newDate: Long) {
        uiState = uiState.copy(entryDate = newDate)
    }

    fun onSeasonSelected(seasonId: String?) {
        uiState = uiState.copy(
            selectedSeasonId = seasonId,
            selectedEpisodeId = null,
            currentSeasonEpisodes = emptyList()
        )

        if (seasonId != null) {
            viewModelScope.launch {
                observeEpisodesBySeasonUseCase(seasonId).collectLatest { episodes ->
                    uiState = uiState.copy(currentSeasonEpisodes = episodes)
                }
            }
        }
    }

    fun onEpisodeSelected(episodeId: String?) {
        uiState = uiState.copy(selectedEpisodeId = episodeId)
    }

    fun selectPersonForMention(person: Person) {
        val currentContent = uiState.content
        val lastAtIndex = currentContent.lastIndexOf('@')
        val updatedContent = if (lastAtIndex != -1) {
            currentContent.substring(0, lastAtIndex) + "@${person.name} "
        } else {
            currentContent + "@${person.name} "
        }

        val updatedPeople = if (uiState.mentionedPeople.any { it.id == person.id }) {
            uiState.mentionedPeople
        } else {
            uiState.mentionedPeople + person
        }

        uiState = uiState.copy(
            content = updatedContent,
            mentionedPeople = updatedPeople,
            isMentionPopupVisible = false,
            mentionQuery = ""
        )
    }

    fun removeMentionedPerson(person: Person) {
        uiState = uiState.copy(
            mentionedPeople = uiState.mentionedPeople.filter { it.id != person.id }
        )
    }

    fun saveJournal(onSuccess: () -> Unit) {
        val title = uiState.title.trim()
        val content = uiState.content.trim()

        if (title.isEmpty() || content.isEmpty()) {
            uiState = uiState.copy(errorMessage = "Başlık dan içerik boş bırakılamaz.")
            return
        }

        viewModelScope.launch {
            val currentId = journalId ?: UUID.randomUUID().toString()
            val currentTime = System.currentTimeMillis()

            val journal = JournalEntry(
                id = currentId,
                title = title,
                content = content,
                entryDate = uiState.entryDate,
                seasonId = uiState.selectedSeasonId,
                episodeId = uiState.selectedEpisodeId,
                createdAt = currentTime,
                updatedAt = currentTime,
                mentionedPeople = uiState.mentionedPeople
            )

            val personIds = uiState.mentionedPeople.map { it.id }
            saveJournalUseCase(journal, personIds)
            uiState = uiState.copy(isSaved = true)
            onSuccess()
        }
    }
}