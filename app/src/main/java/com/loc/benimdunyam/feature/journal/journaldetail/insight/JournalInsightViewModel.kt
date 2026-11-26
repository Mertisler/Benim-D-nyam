package com.loc.benimdunyam.feature.journal.journaldetail.insight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.insight.InsightTagKind
import com.loc.benimdunyam.data.ai.insight.MemoryInsight
import com.loc.benimdunyam.data.ai.insight.MemoryInsightStore
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.domain.model.JournalMood
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class JournalInsightUiState(
    val journalId: String? = null,
    val insight: MemoryInsight? = null,
    /** Anıda seçili ruh hali yoksa öneri gösterilir. */
    val hasMood: Boolean = true
)

/** Anı detayındaki yapay zekâ notları: göster, etiket kaldır, notları sil, ruh hali önerisini uygula. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalInsightViewModel @Inject constructor(
    private val store: MemoryInsightStore,
    private val moods: JournalMoodRepository,
    consent: AiConsentSource
) : ViewModel() {

    private val journalId = MutableStateFlow<String?>(null)

    val state: StateFlow<JournalInsightUiState> = combine(journalId, consent.consent) { id, c -> id to c.isValid }
        .flatMapLatest { (id, allowed) ->
            if (id == null || !allowed) flowOf(JournalInsightUiState(journalId = id))
            else combine(store.observeAll(), moods.observeAll()) { insights, moodRecords ->
                JournalInsightUiState(
                    journalId = id,
                    insight = insights.firstOrNull { it.journalId == id && it.isVisible },
                    hasMood = moodRecords.any { it.journalId == id }
                )
            }.catch { emit(JournalInsightUiState(journalId = id)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalInsightUiState())

    fun show(id: String) {
        if (journalId.value != id) journalId.value = id
    }

    private fun edit(block: (MemoryInsight) -> MemoryInsight) {
        val current = state.value.insight ?: return
        viewModelScope.launch { runCatching { store.upsert(block(current)) } }
    }

    fun removeTag(kind: InsightTagKind, value: String) = edit { i ->
        when (kind) {
            InsightTagKind.TOPIC -> i.copy(topics = i.topics - value, userEdited = true)
            InsightTagKind.PLACE -> i.copy(places = i.places - value, userEdited = true)
            InsightTagKind.EVENT -> i.copy(events = i.events - value, userEdited = true)
        }
    }

    /** Notları gizler; anı değişene kadar yeniden üretilmez. */
    fun hide() = edit { it.copy(hidden = true) }

    fun dismissMood() = edit { it.copy(moodDismissed = true) }

    fun applyMood(mood: JournalMood) {
        val id = state.value.journalId ?: return
        viewModelScope.launch { runCatching { moods.saveMood(id, mood) } }
    }
}
