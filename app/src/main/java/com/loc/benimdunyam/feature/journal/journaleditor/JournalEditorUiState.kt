package com.loc.benimdunyam.feature.journal.journaleditor

import com.loc.benimdunyam.data.local.media.JournalPhoto
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person

data class JournalEditorUiState(
    // Asil editor alani
    val title: String = "",
    val content: String = "",
    val entryDate: Long = System.currentTimeMillis(),
    val selectedMood: JournalMood? = null,
    val mentionedPeople: List<Person> = emptyList(),
    val allPeople: List<Person> = emptyList(),
    val allSeasons: List<LifeSeason> = emptyList(),
    val currentSeasonEpisodes: List<LifeEpisode> = emptyList(),
    val selectedSeasonId: String? = null,
    val selectedEpisodeId: String? = null,
    val isMentionPopupVisible: Boolean = false,
    val mentionQuery: String = "",

    // Ekran durumu
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,

    // Yeni: taslak durumu
    val draftSaveState: DraftSaveState = DraftSaveState.Idle,
    val showRestoreDraftDialog: Boolean = false,
    val isLeaving: Boolean = false,

    // Editördeki fotoğraflar: seçilenler önce taslağa kopyalanır.
    val savedPhotos: List<JournalPhoto> = emptyList(),
    val draftPhotos: List<JournalPhoto> = emptyList(),
    val orderedPhotos: List<JournalPhoto> = emptyList(),
    val coverPhotoId: String? = null,
    val isPhotoWorking: Boolean = false,
    val photoErrorMessage: String? = null
)
