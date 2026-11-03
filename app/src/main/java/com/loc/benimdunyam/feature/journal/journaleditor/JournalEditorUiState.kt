
package com.loc.benimdunyam.feature.journal.journaleditor

import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.LifeSeason
import com.loc.benimdunyam.domain.model.Person


data class JournalEditorUiState(

    // Günlük metin bilgileri
    val title: String = "",
    val content: String = "",
    val entryDate: Long = System.currentTimeMillis(),

    // Seçilen duygu
    val selectedMood: JournalMood? = null,

    // Kişi etiketleme
    val mentionedPeople: List<Person> = emptyList(),
    val allPeople: List<Person> = emptyList(),

    // Sezon ve bölüm bilgileri
    val allSeasons: List<LifeSeason> = emptyList(),
    val currentSeasonEpisodes: List<LifeEpisode> = emptyList(),

    // Seçili sezon ve bölüm
    val selectedSeasonId: String? = null,
    val selectedEpisodeId: String? = null,

    // Mention durumu
    val isMentionPopupVisible: Boolean = false,
    val mentionQuery: String = "",

    // Ekran durumları
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,

    // Hata mesajı
    val errorMessage: String? = null
)
