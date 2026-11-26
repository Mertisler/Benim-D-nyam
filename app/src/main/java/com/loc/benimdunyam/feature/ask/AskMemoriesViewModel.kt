package com.loc.benimdunyam.feature.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.AiException
import com.loc.benimdunyam.data.ai.history.AskHistoryStore
import com.loc.benimdunyam.data.ai.history.AskedQuestion
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AskSource(val number: Int, val journalId: String, val title: String, val dateMillis: Long?)

data class ShownAnswer(
    val question: String,
    val answer: String,
    val sources: List<AskSource>,
    val notFound: Boolean
)

data class AskMemoriesUiState(
    val enabled: Boolean = false,
    val hasAccount: Boolean = false,
    val input: String = "",
    val step: AskStep? = null,
    val shown: ShownAnswer? = null,
    val error: String? = null,
    val history: List<AskedQuestion> = emptyList(),
    val examples: List<String> = emptyList(),
    val hasJournals: Boolean = true
) {
    val busy: Boolean get() = step != null
}

private data class Local(
    val input: String = "",
    val step: AskStep? = null,
    val shownQuestion: String? = null,
    val shownAnswer: String? = null,
    val shownSources: List<String> = emptyList(),
    val shownNotFound: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AskMemoriesViewModel @Inject constructor(
    private val service: AskMemoriesService,
    private val history: AskHistoryStore,
    observeJournals: ObserveJournalsUseCase,
    consent: AiConsentSource,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val local = MutableStateFlow(Local())
    private var job: Job? = null

    val state: StateFlow<AskMemoriesUiState> = combine(
        consent.consent,
        local,
        observeJournals().catch { emit(emptyList()) }.onStart { emit(emptyList()) },
        history.observeRecent().catch { emit(emptyList()) }.onStart { emit(emptyList()) }
    ) { c, l, journals, past ->
        val byId = journals.associateBy { it.id }
        AskMemoriesUiState(
            enabled = c.isValid,
            hasAccount = auth.currentUser != null,
            input = l.input,
            step = l.step,
            shown = l.shownAnswer?.let { answer ->
                ShownAnswer(
                    question = l.shownQuestion.orEmpty(),
                    answer = answer,
                    sources = l.shownSources.mapIndexed { i, id ->
                        val j = byId[id]
                        AskSource(i + 1, id, j?.title?.ifBlank { "Başlıksız anı" } ?: "Silinmiş anı", j?.entryDate)
                    },
                    notFound = l.shownNotFound
                )
            },
            error = l.error,
            history = if (c.isValid) past else emptyList(),
            examples = examples(journals),
            hasJournals = journals.isNotEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskMemoriesUiState())

    private fun examples(journals: List<JournalEntry>): List<String> {
        val topPerson = journals.flatMap { it.mentionedPeople }.groupingBy { it.name }.eachCount()
            .maxByOrNull { it.value }?.key
        return listOfNotNull(
            topPerson?.let { "$it ile ilk anımız neydi?" },
            "Geçen yaz neler yaptım?",
            "Kendimi en mutlu hissettiğim anlar hangileriydi?",
            "Geçen yıl bu zamanlar neler yaşıyordum?"
        )
    }

    fun onInput(text: String) = local.update { it.copy(input = text.take(AskMemoriesService.MAX_QUESTION), error = null) }

    fun useExample(text: String) = local.update { it.copy(input = text, error = null) }

    fun ask() {
        val q = local.value.input.trim()
        if (q.isEmpty() || local.value.step != null) return
        job = viewModelScope.launch {
            local.update { it.copy(step = AskStep.SEARCHING, error = null) }
            try {
                val r = service.ask(q, onStep = { step -> local.update { it.copy(step = step) } })
                local.update {
                    it.copy(step = null, input = "", shownQuestion = r.question, shownAnswer = r.answer,
                        shownSources = r.sourceIds, shownNotFound = r.notFound)
                }
            } catch (c: CancellationException) {
                local.update { it.copy(step = null) }
                throw c
            } catch (e: AiException) {
                local.update { it.copy(step = null, error = e.message) }
            } catch (e: Exception) {
                local.update { it.copy(step = null, error = "Soru cevaplanamadı. Biraz sonra tekrar dene.") }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        local.update { it.copy(step = null) }
    }

    fun show(item: AskedQuestion) = local.update {
        it.copy(shownQuestion = item.question, shownAnswer = item.answer, shownSources = item.sources,
            shownNotFound = item.notFound, error = null)
    }

    fun closeAnswer() = local.update { it.copy(shownAnswer = null, shownQuestion = null, shownSources = emptyList()) }

    fun delete(item: AskedQuestion) {
        viewModelScope.launch { runCatching { history.delete(item.id) } }
    }

    fun clearHistory() {
        viewModelScope.launch { runCatching { history.clear() } }
    }
}
