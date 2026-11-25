package com.loc.benimdunyam.feature.people.detail.relationship

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Kişinin etiketlendiği bir anıdaki fotoğraf. */
data class PersonTogetherPhoto(
    val journalId: String,
    val photoId: String,
    val path: String,
    val entryDate: Long
)

/**
 * Kişi detayına ek içgörü verileri: birlikte fotoğraflar ve adı geçen ama
 * etiketlenmemiş anılar. Yalnızca OKUR; hiçbir anıyı veya fotoğrafı değiştirmez.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonRelationshipViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeJournalsUseCase: ObserveJournalsUseCase,
    private val photoStore: JournalPhotoStore
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"])
    private val personName = MutableStateFlow("")

    private val allJournals = observeJournalsUseCase()

    private val personJournals = allJournals
        .map { journals -> journals.filter { j -> j.mentionedPeople.any { it.id == personId } } }
        .distinctUntilChanged()

    /** En yeni anılardan başlayarak en çok [MAX_PHOTOS] fotoğraf. */
    val photos: StateFlow<List<PersonTogetherPhoto>> = personJournals
        .mapLatest { journals ->
            val result = mutableListOf<PersonTogetherPhoto>()
            for (journal in journals.sortedByDescending { it.entryDate }) {
                if (result.size >= MAX_PHOTOS) break
                val list = runCatching { photoStore.list(journal.id) }.getOrDefault(emptyList())
                list.forEach { photo ->
                    if (result.size < MAX_PHOTOS) {
                        result += PersonTogetherPhoto(journal.id, photo.id, photo.absolutePath, journal.entryDate)
                    }
                }
            }
            result
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val untaggedMentions: StateFlow<List<UntaggedMention>> = combine(allJournals, personName) { journals, name ->
        UntaggedMentionFinder.find(
            personId = personId,
            personName = name,
            journals = journals.map { journal ->
                UntaggedMentionFinder.Candidate(
                    journalId = journal.id,
                    title = journal.title,
                    content = journal.content,
                    entryDate = journal.entryDate,
                    taggedPersonIds = journal.mentionedPeople.map { it.id }.toSet()
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Kişinin güncel adı ekrandan gelir (düzenlendiğinde öneriler de güncellenir). */
    fun bindName(name: String) {
        personName.value = name
    }

    private companion object {
        const val MAX_PHOTOS = 30
    }
}
