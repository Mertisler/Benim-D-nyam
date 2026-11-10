package com.loc.benimdunyam.feature.yearreview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.feature.yearreview.mood.YearMoodCalculator
import com.loc.benimdunyam.feature.yearreview.mood.YearMoodReport
import com.loc.benimdunyam.feature.yearreview.featured.YearFeaturedMemoryRepository
import com.loc.benimdunyam.domain.usecase.ObserveJournalsUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

data class YearReviewUiState(
    val isLoading: Boolean = true,
    val years: List<Int> = emptyList(),
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val report: YearReviewReport? = null,
    val coverPaths: Map<String, String> = emptyMap(),
    val featuredMemories: List<com.loc.benimdunyam.domain.model.JournalEntry> = emptyList(),
    val moodReport: YearMoodReport? = null,
    val error: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class YearReviewViewModel @Inject constructor(
    observeJournalsUseCase: ObserveJournalsUseCase,
    observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    private val photoStore: JournalPhotoStore,
    private val featuredRepository: YearFeaturedMemoryRepository,
    private val journalMoodRepository: JournalMoodRepository
) : ViewModel() {
    // null: varsayılan olarak anı içeren en güncel yıl (yoksa mevcut yıl).
    private val requestedYear = MutableStateFlow<Int?>(null)
    private val refreshSignal = MutableStateFlow(0)

    fun selectYear(year: Int) { requestedYear.value = year }
    fun refreshCovers() { refreshSignal.value += 1 }

    fun moveFeatured(journalId: String, direction: Int) {
        viewModelScope.launch {
            try { featuredRepository.move(journalId, direction) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("YearReview", "Özel anı sırası değişmedi", e) }
        }
    }

    fun removeFeatured(journalId: String) {
        viewModelScope.launch {
            try { featuredRepository.remove(journalId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("YearReview", "Özel anı kaldırılamadı", e) }
        }
    }

    init {
        // İstenmeyen eski seçimleri temizler (anı silme / tarih değiştirme).
        // İlk gözlemden sonra, yalnızca değişen anı kimliği+yıl çiftlerinde çalışır.
        viewModelScope.launch(Dispatchers.IO) {
            observeJournalsUseCase()
                .distinctUntilChanged { old, new ->
                    old.size == new.size &&
                            old.map { it.id to YearFeaturedMemoryRepository.yearOf(it.entryDate) }.toSet() ==
                            new.map { it.id to YearFeaturedMemoryRepository.yearOf(it.entryDate) }.toSet()
                }
                .collect { journals ->
                    try { featuredRepository.reconcile(journals) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Log.e("YearReview", "Özel anılar eşitlenemedi", e) }
                }
        }
    }

    // Günlükler ve duygu kayıtları tek akışta birleşir; duygu değiştiğinde yıllık özet yenilenir.
    private val journalsWithMoods = combine(
        observeJournalsUseCase(),
        journalMoodRepository.observeAll()
    ) { journals, moods -> journals to moods }

    val uiState: StateFlow<YearReviewUiState> = combine(
        journalsWithMoods,
        observeLifeSeasonsUseCase(),
        featuredRepository.observeAll(),
        requestedYear,
        refreshSignal
    ) { journalsAndMoods, seasons, selections, requested, _ ->
        val (journals, moods) = journalsAndMoods
        val years = YearReviewCalculator.availableYears(
            journals, Calendar.getInstance().get(Calendar.YEAR)
        )
        val selected = requested?.takeIf { it in years }
            ?: years.firstOrNull { yr ->
                journals.any { journal ->
                    Calendar.getInstance().apply { timeInMillis = journal.entryDate }
                        .get(Calendar.YEAR) == yr
                }
            }
            ?: years.first()
        val journalById = journals.associateBy { it.id }
        val featured = selections.asSequence()
            .filter { it.year == selected }
            .sortedWith(compareBy({ it.position }, { it.addedAt }, { it.journalId }))
            .mapNotNull { item -> journalById[item.journalId] }
            .filter { YearFeaturedMemoryRepository.yearOf(it.entryDate) == selected }
            .take(YearFeaturedMemoryRepository.MAX_PER_YEAR)
            .toList()
        YearReviewUiState(
            isLoading = false,
            years = years,
            selectedYear = selected,
            report = YearReviewCalculator.calculate(selected, journals, seasons),
            moodReport = YearMoodCalculator.calculate(
                year = selected,
                journals = journals,
                moodNamesByJournalId = moods.associate { it.journalId to it.moodName }
            ),
            featuredMemories = featured
        )
    }.flatMapLatest { base ->
        flow {
            emit(base) // Fotoğraflar yüklenmeden metin görünür.
            // Önce özel anılar, sonra her ayın kapak adayları tur tur okunur.
            // Böylece yıl sonundaki fotoğraflar, yıl başındaki ayları gölgelemez.
            val monthly = base.report?.monthlyStories.orEmpty()
            val monthlyCandidates = buildList {
                for (rank in 0..2) {
                    for (story in monthly) {
                        story.coverCandidates.getOrNull(rank)?.let { add(it) }
                    }
                }
            }
            val candidates = (base.featuredMemories + monthlyCandidates +
                    base.report?.coverCandidates.orEmpty()).distinctBy { it.id }.take(100)
            val found = linkedMapOf<String, String>()
            for (journal in candidates) {
                if (found.size >= 24) break
                val path = try {
                    withContext(Dispatchers.IO) { photoStore.coverPhoto(journal.id)?.absolutePath }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("YearReview", "Anı kapağı okunamadı: ${journal.id}", error)
                    null
                }
                if (path != null) {
                    found[journal.id] = path
                    emit(base.copy(coverPaths = found.toMap()))
                }
            }
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e("YearReview", "Yıl özeti yüklenemedi", error)
        emit(YearReviewUiState(isLoading = false, error = "Yıl hikâyesi şu anda yüklenemedi."))
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YearReviewUiState()
    )
}
