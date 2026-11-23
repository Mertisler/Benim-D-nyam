package com.loc.benimdunyam.feature.settings.cloudaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.sync.ConflictCenterService
import com.loc.benimdunyam.data.sync.remote.JournalConflictCard
import com.loc.benimdunyam.data.sync.work.SyncFailureMessages
import com.loc.benimdunyam.domain.sync.ConflictChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConflictCenterUiState(
    val uid: String? = null,
    val loading: Boolean = false,
    val cards: List<JournalConflictCard> = emptyList(),
    val technicalOnly: Int = 0,
    /** İşlemi süren kartın anı kimliği; o kartın düğmeleri kilitlenir. */
    val workingOn: String? = null,
    val message: String? = null,
    val error: String? = null,
    val loaded: Boolean = false
)

/** Aşama 2 — şifresiz, iki sütunlu çakışma ekranı. */
@HiltViewModel
class ConflictCenterViewModel @Inject constructor(
    private val service: ConflictCenterService
) : ViewModel() {

    private val mutable = MutableStateFlow(ConflictCenterUiState())
    val state = mutable.asStateFlow()

    fun select(uid: String) {
        if (mutable.value.uid != uid) mutable.value = ConflictCenterUiState(uid = uid)
        load()
    }

    fun load() {
        val uid = mutable.value.uid ?: return
        if (mutable.value.loading) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val report = service.load(uid)
                if (mutable.value.uid == uid) {
                    mutable.update {
                        it.copy(loading = false, loaded = true, cards = report.cards,
                            technicalOnly = report.technicalOnly)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutable.value.uid == uid) {
                    mutable.update {
                        it.copy(loading = false, loaded = true, error = describe(error))
                    }
                }
            }
        }
    }

    fun choose(card: JournalConflictCard, choice: ConflictChoice) {
        val uid = mutable.value.uid ?: return
        if (mutable.value.workingOn != null) return
        mutable.update { it.copy(workingOn = card.journalId, message = null, error = null) }
        viewModelScope.launch {
            try {
                service.choose(uid, card, choice)
                if (mutable.value.uid == uid) {
                    mutable.update { state ->
                        state.copy(
                            workingOn = null,
                            cards = state.cards.filterNot { it.journalId == card.journalId },
                            message = successText(card, choice)
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutable.value.uid == uid) {
                    mutable.update { it.copy(workingOn = null, error = describe(error)) }
                    // Bulut ya da anı arada değiştiyse güncel listeyi göster.
                    load()
                }
            }
        }
    }

    fun dismissMessage() = mutable.update { it.copy(message = null, error = null) }

    private fun successText(card: JournalConflictCard, choice: ConflictChoice): String {
        val title = (card.localTitle ?: card.remoteTitle)?.let { "\"$it\"" } ?: "Anı"
        return when (choice) {
            ConflictChoice.KEEP_THIS_DEVICE ->
                if (card.localDeleted) "$title silinmiş olarak kalacak; diğer cihazlara da iletilecek."
                else "$title bu telefondaki haliyle kalacak; diğer cihazlara da iletilecek."
            ConflictChoice.TAKE_OTHER_DEVICE ->
                if (card.localDeleted) "$title diğer cihazdaki haliyle geri getirildi."
                else "$title diğer cihazdaki haliyle güncellendi."
            ConflictChoice.KEEP_BOTH ->
                "$title bu telefondaki haliyle kaldı; diğer cihazdaki metin ayrı bir anı olarak eklendi."
            ConflictChoice.MOVE_TO_TRASH ->
                "$title Son silinenler'e taşındı. 30 gün içinde geri getirebilirsin."
        }
    }

    private fun describe(error: Exception): String = when (error) {
        is IllegalStateException ->
            error.message?.takeIf { "eşitlemeyi aç" in it }
                ?: "Anı ya da bulut kaydı bu sırada değişti. Liste yenilendi; tekrar seç."
        else -> SyncFailureMessages.describe(error)
    }
}
