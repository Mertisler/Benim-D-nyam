package com.loc.benimdunyam.feature.narrative

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.AiException
import com.loc.benimdunyam.data.ai.narrative.NarrativeScope
import com.loc.benimdunyam.data.ai.narrative.NarrativeService
import com.loc.benimdunyam.data.ai.narrative.NarrativeStatus
import com.loc.benimdunyam.data.ai.narrative.NarrativeStore
import com.loc.benimdunyam.data.ai.narrative.StoredNarrative
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.feature.ask.AskSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiNarrativeUiState(
    /** Yapay zekâ kapalıysa kart hiç gösterilmez. */
    val visible: Boolean = false,
    val title: String = "",
    val memoryCount: Int = 0,
    val enough: Boolean = false,
    val text: String? = null,
    val sources: List<AskSource> = emptyList(),
    /** Yazı üretildikten sonra anılar değişti. */
    val stale: Boolean = false,
    val generating: Boolean = false,
    val error: String? = null
)

private data class Local(val generating: Boolean = false, val error: String? = null)

/** Hikâyem, Yıl Özeti ve kişi detayındaki "hikâyesi" kartı. Ekran [bind] ile kapsamı verir. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiNarrativeViewModel @Inject constructor(
    private val service: NarrativeService,
    store: NarrativeStore,
    consent: AiConsentSource,
    observeJournals: ObserveJournalsUseCase
) : ViewModel() {

    private val scope = MutableStateFlow<NarrativeScope?>(null)
    private val local = MutableStateFlow(Local())

    private data class Snapshot(
        val scope: NarrativeScope,
        val stored: StoredNarrative?,
        val status: NarrativeStatus,
        val journals: Map<String, JournalEntry>
    )

    private val snapshot = combine(
        scope.filterNotNull(),
        observeJournals().catch { emit(emptyList()) }
    ) { s, journals -> s to journals }
        .flatMapLatest { (s, journals) ->
            store.observe(s.key).catch { emit(null) }.mapLatest { stored ->
                val status = runCatching { service.status(s) }.getOrDefault(NarrativeStatus(0, null))
                Snapshot(s, stored, status, journals.associateBy { it.id })
            }
        }

    val state: StateFlow<AiNarrativeUiState> = combine(consent.consent, local, snapshot) { c, l, snap ->
        if (!c.isValid) return@combine AiNarrativeUiState()
        val stored = snap.stored
        AiNarrativeUiState(
            visible = true,
            title = snap.scope.title,
            memoryCount = snap.status.memoryCount,
            enough = snap.status.enough,
            text = stored?.text,
            sources = stored?.sources.orEmpty().mapIndexed { i, id ->
                val j = snap.journals[id]
                AskSource(i + 1, id, j?.title?.ifBlank { "Başlıksız anı" } ?: "Silinmiş anı", j?.entryDate)
            },
            stale = stored != null && stored.inputHash != snap.status.currentHash,
            generating = l.generating,
            error = l.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiNarrativeUiState())

    fun bind(value: NarrativeScope) {
        if (scope.value != value) {
            scope.value = value
            local.value = Local()
        }
    }

    fun generate() {
        val s = scope.value ?: return
        if (local.value.generating) return
        viewModelScope.launch {
            local.update { it.copy(generating = true, error = null) }
            try {
                service.generate(s)
                local.update { it.copy(generating = false) }
            } catch (c: CancellationException) {
                local.update { it.copy(generating = false) }
                throw c
            } catch (e: AiException) {
                local.update { it.copy(generating = false, error = e.message) }
            } catch (e: IllegalArgumentException) {
                local.update { it.copy(generating = false, error = e.message) }
            } catch (e: Exception) {
                local.update { it.copy(generating = false, error = "Hikâye yazılamadı. Biraz sonra tekrar dene.") }
            }
        }
    }
}
