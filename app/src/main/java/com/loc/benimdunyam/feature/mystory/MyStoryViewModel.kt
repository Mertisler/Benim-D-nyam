package com.loc.benimdunyam.feature.mystory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.feature.home.analytics.LocalAnalyticsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.insight.MemoryInsightStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class MyStoryUiState(
    val loading: Boolean = true,
    val period: LocalAnalyticsPeriod = LocalAnalyticsPeriod.LAST_12_MONTHS,
    val report: MyStoryReport = MyStoryReport(),
    val error: String? = null
)

/** "Hikâyem" — tüm hesaplar cihazda, salt okunur. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyStoryViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    private val moodRepository: JournalMoodRepository,
    private val photoStore: JournalPhotoStore,
    private val insightStore: MemoryInsightStore,
    consent: AiConsentSource
) : ViewModel() {

    /** Yapay zekâ kapalıysa analizler gösterilmez (izin kapatılınca silinirler de). */
    private val insights = consent.consent.flatMapLatest { c ->
        if (!c.isValid) flowOf(emptyList())
        else insightStore.observeAll().catch { emit(emptyList()) }
    }.onStart { emit(emptyList()) }

    private val period = MutableStateFlow(LocalAnalyticsPeriod.LAST_12_MONTHS)

    private val journals = observeJournalsUseCase()

    /** Fotoğraf sayıları yalnızca anı listesi değişince yeniden okunur. */
    private val photoCounts = journals
        .map { list -> list.map { it.id } }
        .distinctUntilChanged()
        .mapLatest { ids ->
            ids.associateWith { id -> runCatching { photoStore.list(id).size }.getOrDefault(0) }
        }
        .onStart { emit(emptyMap()) }

    val uiState: StateFlow<MyStoryUiState> = combine(
        journals,
        observeLifeSeasonsUseCase(),
        moodRepository.observeAll(),
        combine(photoCounts, insights) { photos, list -> photos to list },
        period
    ) { journalList, seasons, moods, (photos, insightList), selected ->
        val visible = insightList.filter { it.isVisible }
        MyStoryUiState(
            loading = false,
            period = selected,
            report = MyStoryCalculator.calculate(
                journals = journalList,
                seasons = seasons,
                moodNamesByJournalId = moods.associate { it.journalId to it.moodName },
                photoCountByJournalId = photos,
                period = selected,
                now = System.currentTimeMillis(),
                topicsByJournalId = visible.associate { it.journalId to it.topics },
                moodSuggestionsByJournalId = visible
                    .filter { !it.moodDismissed && it.moodSuggestion != null }
                    .associate { it.journalId to it.moodSuggestion!! }
            )
        )
    }.flowOn(Dispatchers.Default)
        .catch { emit(MyStoryUiState(loading = false, error = "Hikâyen şu anda okunamadı. Anıların değiştirilmedi.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyStoryUiState())

    fun selectPeriod(value: LocalAnalyticsPeriod) {
        period.value = value
    }

    fun applyMoodSuggestion(suggestion: MoodSuggestion) {
        viewModelScope.launch { runCatching { moodRepository.saveMood(suggestion.journalId, suggestion.mood) } }
    }

    fun dismissMoodSuggestion(journalId: String) {
        viewModelScope.launch {
            runCatching { insightStore.get(journalId)?.let { insightStore.upsert(it.copy(moodDismissed = true)) } }
        }
    }
}
